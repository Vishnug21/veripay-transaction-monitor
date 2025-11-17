package com.example.veripaytransactionmonitor

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

object UserHelper {

    fun ensureUserDocExists() {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val user = auth.currentUser

        if (user == null) {
            Log.d("USER-HELPER", "No signed-in user, skipping user doc check")
            return
        }

        val userRef = db.collection("users").document(user.uid)
        userRef.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    Log.d("USER-HELPER", "User doc already exists for ${user.email}")
                } else {
                    // derive a default storeId (can be customized)
                    val defaultStoreId = user.email?.substringBefore("@") ?: "store_${user.uid.take(6)}"
                    val newUserData = mapOf(
                        "email" to user.email,
                        "storeId" to defaultStoreId,
                        "createdAt" to Timestamp.now()
                    )

                    userRef.set(newUserData)
                        .addOnSuccessListener {
                            Log.d("USER-HELPER", "Created user doc for ${user.email} with storeId=$defaultStoreId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("USER-HELPER", "Failed to create user doc: ${e.message}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("USER-HELPER", "Error checking user doc: ${e.message}", e)
            }
    }
}
