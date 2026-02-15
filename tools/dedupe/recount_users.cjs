// recount_users.cjs
const admin = require("firebase-admin");
const path = require("path");
const fs = require("fs");

const SERVICE_ACCOUNT_PATH = path.join(__dirname, "serviceAccountKey.json");

const PRED_COLLECTION = "predicciones";
const USERS_COLLECTION = "users";

function toStr(v) {
  if (v === null || v === undefined) return "";
  return String(v);
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

  console.log(`Leyendo "${PRED_COLLECTION}" para recalcular contadores...`);
  const snap = await db.collection(PRED_COLLECTION).get();
  console.log(`Predicciones encontradas: ${snap.size}`);

  // uid -> { total, validas, no_validas }
  const counts = new Map();

  for (const doc of snap.docs) {
    const data = doc.data();
    const uid = toStr(data.uid_medico).trim();
    if (!uid) continue;

    const valido = toStr(data.valido).trim(); // "Si" o "No"

    if (!counts.has(uid)) {
      counts.set(uid, { total: 0, validas: 0, no_validas: 0 });
    }
    const c = counts.get(uid);
    c.total += 1;
    if (valido === "Si") c.validas += 1;
    else if (valido === "No") c.no_validas += 1;
  }

  console.log(`Usuarios a actualizar: ${counts.size}`);

  // Batch writes (máx 500 ops por batch)
  let batch = db.batch();
  let ops = 0;
  let updated = 0;

  for (const [uid, c] of counts.entries()) {
    const ref = db.collection(USERS_COLLECTION).doc(uid);

    batch.set(
      ref,
      {
        numeroPredicciones: c.total,
        predicciones_validas: c.validas,
        predicciones_no_validas: c.no_validas,
      },
      { merge: true }
    );

    ops++;
    updated++;

    if (ops >= 450) { // margen de seguridad
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }

  if (ops > 0) await batch.commit();

  console.log("\n=== RESUMEN ===");
  console.log("Usuarios actualizados:", updated);
  console.log("Listo.");
}

main().catch((e) => {
  console.error("Fallo fatal:", e);
  process.exit(1);
});
