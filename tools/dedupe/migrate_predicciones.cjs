// migrate_predicciones.js
const admin = require("firebase-admin");
const crypto = require("crypto");
const path = require("path");
const fs = require("fs");

const SERVICE_ACCOUNT_PATH = path.join(__dirname, "serviceAccountKey.json");

// === CONFIG ===
const COLLECTION = "predicciones";
const PREFIX = "pred_v2_";

// Campos que definen "duplicado" (sin normalización)
const KEY_FIELDS = [
  "prediction_mode",
  "edad",
  "femenino",
  "capnometria",
  "causa_cardiaca",
  "cardio_manual",
  "rec_pulso",
  "valido",
];

function sha256Hex(input) {
  return crypto.createHash("sha256").update(input, "utf8").digest("hex");
}

function toStr(v) {
  // Firestore puede tener números, strings, null...
  if (v === null || v === undefined) return "";
  return String(v);
}

function buildCanonical(data) {
  // SIN normalización, exactamente como está en Firestore
  const parts = KEY_FIELDS.map((k) => toStr(data[k]));
  return parts.join("|");
}

function computeDocIdFromData(data) {
  const canonical = buildCanonical(data);
  return PREFIX + sha256Hex(canonical);
}

async function main() {
  if (!fs.existsSync(SERVICE_ACCOUNT_PATH)) {
    console.error("No encuentro serviceAccountKey.json en:", SERVICE_ACCOUNT_PATH);
    process.exit(1);
  }

  admin.initializeApp({
    credential: admin.credential.cert(require(SERVICE_ACCOUNT_PATH)),
  });

  const db = admin.firestore();

  console.log(`Leyendo colección "${COLLECTION}"...`);
  const snap = await db.collection(COLLECTION).get();
  console.log(`Docs encontrados: ${snap.size}`);

  let migrated = 0;
  let deletedDuplicates = 0;
  let skipped = 0;
  let alreadyOk = 0;
  let errors = 0;

  // Para detectar duplicados también entre los que YA sean pred_v2_...
  const seen = new Map(); // docId -> keptOriginalDocId

  for (const doc of snap.docs) {
    const data = doc.data();

    const missing = KEY_FIELDS.filter((k) => !(k in data));
    if (missing.length > 0) {
      try {
        await db.collection(COLLECTION).doc(doc.id).delete();
        deletedDuplicates++;
        console.log(
          `[DEL-INVALID] ${doc.id} eliminado (faltan campos: ${missing.join(", ")})`
        );
      } catch (e) {
        errors++;
        console.log(`[ERR] borrando inválido ${doc.id}:`, e.message);
      }
      continue;
    }

    const deterministicId = computeDocIdFromData(data);

    // Si ya vimos ese deterministicId, este es duplicado -> borrar doc actual
    if (seen.has(deterministicId)) {
      try {
        await db.collection(COLLECTION).doc(doc.id).delete();
        deletedDuplicates++;
        console.log(
          `[DEL-DUP] ${doc.id} duplicado de ${seen.get(deterministicId)} -> key=${deterministicId}`
        );
      } catch (e) {
        errors++;
        console.log(`[ERR] borrando duplicado ${doc.id}:`, e.message);
      }
      continue;
    }

    // Primera vez que vemos esa clave
    seen.set(deterministicId, doc.id);

    // Si ya está guardado con el id determinista, lo dejamos
    if (doc.id === deterministicId) {
      alreadyOk++;
      continue;
    }

    // Migrar: crear nuevo doc determinista y borrar el antiguo (id aleatorio)
    const oldRef = db.collection(COLLECTION).doc(doc.id);
    const newRef = db.collection(COLLECTION).doc(deterministicId);

    try {
      await db.runTransaction(async (tx) => {
        const newSnap = await tx.get(newRef);
        if (newSnap.exists) {
          // Ya existe el determinista => el viejo es duplicado
          tx.delete(oldRef);
          return { action: "delete_old_only" };
        } else {
          // Crear determinista con la data exacta, y borrar el viejo
          tx.set(newRef, data, { merge: false });
          tx.delete(oldRef);
          return { action: "migrated" };
        }
      });

      // Ojo: el transaction de arriba puede haber sido "delete_old_only" o "migrated"
      // pero aquí no tenemos el return directo (depende del SDK). Lo contamos simple:
      migrated++;
      console.log(`[MIGRATE] ${doc.id} -> ${deterministicId}`);
    } catch (e) {
      errors++;
      console.log(`[ERR] migrando ${doc.id} -> ${deterministicId}:`, e.message);
    }
  }

  console.log("\n=== RESUMEN ===");
  console.log("Migrados (oldId -> pred_v2_...):", migrated);
  console.log("Duplicados borrados:", deletedDuplicates);
  console.log("Ya estaban OK (id determinista):", alreadyOk);
  console.log("Saltados (faltan campos):", skipped);
  console.log("Errores:", errors);
  console.log("\nListo.");
}

main().catch((e) => {
  console.error("Fallo fatal:", e);
  process.exit(1);
});