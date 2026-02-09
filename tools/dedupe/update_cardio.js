import admin from "firebase-admin";
import fs from "fs";

const serviceAccount = JSON.parse(fs.readFileSync("./serviceAccountKey.json", "utf8"));

// Si ya inicializaste admin en otro script y lo ejecutas en el mismo proceso,
// esto evita error de "already exists":
if (!admin.apps.length) {
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });
}

const db = admin.firestore();

const FIELD = "cardio_manual"; // <-- si en tu Firestore se llama distinto, cámbialo aquí

function toManualMecanica(value) {
  // Soporta números, booleanos y strings
  if (value === 1 || value === "1" || value === true) return "Manual";
  if (value === 0 || value === "0" || value === false) return "Mecánica";

  if (value == null) return null;

  const s = value.toString().trim().toLowerCase();

  // por si hay datos viejos tipo Si/No
  if (s === "si" || s === "sí") return "Manual";
  if (s === "no") return "Mecánica";

  // ya normalizado
  if (s === "manual") return "Manual";
  if (s === "mecánica" || s === "mecanica") return "Mecánica";

  return null; // no tocar si viene algo raro
}

async function main() {
  const col = db.collection("predicciones");

  let scanned = 0;
  let updated = 0;

  let lastDoc = null;
  const pageSize = 500;

  while (true) {
    let q = col.orderBy(admin.firestore.FieldPath.documentId()).limit(pageSize);
    if (lastDoc) q = q.startAfter(lastDoc);

    const snap = await q.get();
    if (snap.empty) break;

    let batch = db.batch();
    let ops = 0;

    for (const doc of snap.docs) {
      scanned++;
      const data = doc.data();
      const current = data[FIELD];
      const mapped = toManualMecanica(current);

      if (!mapped) continue; // no sabemos mapearlo → no tocar

      // Evita writes innecesarios
      if (typeof current === "string" && current.trim().toLowerCase() === mapped.toLowerCase()) {
        continue;
      }

      batch.update(doc.ref, { [FIELD]: mapped });
      ops++;
      updated++;

      if (ops >= 450) {
        await batch.commit();
        batch = db.batch();
        ops = 0;
      }
    }

    if (ops > 0) await batch.commit();
    lastDoc = snap.docs[snap.docs.length - 1];
  }

  console.log(`Listo. Escaneadas: ${scanned} | Actualizadas: ${updated}`);
}

main().catch((e) => {
  console.error("ERROR:", e);
  process.exit(1);
});