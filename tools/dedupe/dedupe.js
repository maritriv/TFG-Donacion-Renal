import admin from "firebase-admin";
import crypto from "crypto";
import fs from "fs";

const serviceAccount = JSON.parse(fs.readFileSync("./serviceAccountKey.json", "utf8"));

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

function norm(v) {
  return (v ?? "").toString().trim().toLowerCase();
}

function buildDedupKey(d) {
  return [
    norm(d.uid_medico),
    norm(d.prediction_mode),
    norm(d.edad),
    norm(d.femenino),
    norm(d.capnometria),
    norm(d.causa_cardiaca),
    norm(d.cardio_manual),
    norm(d.rec_pulso),
    norm(d.valido),
  ].join("|");
}

function hashKey(key) {
  return crypto.createHash("sha256").update(key).digest("hex");
}

async function main() {
  const col = db.collection("predicciones");

  let deleted = 0;
  let kept = 0;

  // Paginación por seguridad
  let lastDoc = null;
  const pageSize = 500;

  const seen = new Set();

  while (true) {
    let q = col.orderBy(admin.firestore.FieldPath.documentId()).limit(pageSize);
    if (lastDoc) q = q.startAfter(lastDoc);

    const snap = await q.get();
    if (snap.empty) break;

    let batch = db.batch();
    let ops = 0;

    for (const doc of snap.docs) {
      const data = doc.data();
      const key = hashKey(buildDedupKey(data));

      if (seen.has(key)) {
        batch.delete(doc.ref);
        ops++;
        deleted++;

        // commit parcial para no llegar al límite
        if (ops >= 450) {
          await batch.commit();
          batch = db.batch();
          ops = 0;
        }
      } else {
        seen.add(key);
        kept++;
      }
    }

    if (ops > 0) await batch.commit();
    lastDoc = snap.docs[snap.docs.length - 1];
  }

  console.log(`Listo. Conservadas: ${kept} | Eliminadas: ${deleted}`);
}

main().catch((e) => {
  console.error("ERROR:", e);
  process.exit(1);
});

// para ejecutar: cd tools/dedupe; node dedupe.js
