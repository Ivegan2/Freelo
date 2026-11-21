const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

initializeApp();

/** Allows an active administrator to delete another account and its profile. */
exports.adminDeleteUser = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Inicia sesión como administrador.");
  }

  const adminSnapshot = await getFirestore().collection("users").doc(request.auth.uid).get();
  if (adminSnapshot.data()?.role !== "admin" || adminSnapshot.data()?.active !== true) {
    throw new HttpsError("permission-denied", "Solo un administrador activo puede eliminar cuentas.");
  }

  const uid = request.data?.uid;
  if (typeof uid !== "string" || uid.length === 0) {
    throw new HttpsError("invalid-argument", "Falta el identificador de la cuenta.");
  }
  if (uid === request.auth.uid) {
    throw new HttpsError("failed-precondition", "No puedes eliminar tu propia cuenta activa.");
  }

  const target = getFirestore().collection("users").doc(uid);
  const targetSnapshot = await target.get();
  if (!targetSnapshot.exists) {
    throw new HttpsError("not-found", "No se encontró el perfil.");
  }

  // Remove the credential first so a Firestore failure cannot leave a usable login.
  // A prior attempt may have removed Auth successfully but failed before deleting Firestore.
  try {
    await getAuth().deleteUser(uid);
  } catch (error) {
    if (error.code !== "auth/user-not-found") {
      throw error;
    }
    console.info(`Auth account ${uid} was already removed; cleaning its remaining profile.`);
  }
  await target.delete();
  return { deleted: true };
});
