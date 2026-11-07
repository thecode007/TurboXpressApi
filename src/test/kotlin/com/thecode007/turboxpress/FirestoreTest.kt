package com.thecode007.turboxpress

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

class FirestoreTest {
    @Test
    fun testFirestore() {
        val file = File("src/main/resources/firebase-service-account.json")
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(FileInputStream(file)))
            .setProjectId("turboxpress-b159c")
            .build()

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options)
        }

        val firestore = FirestoreClient.getFirestore()
        try {
            val doc = firestore.collection("order_events").document("latest").get().get()
            println("Firestore test result: exists=${doc.exists()}, data=${doc.data}")
        } catch (e: Exception) {
            println("Firestore test error: ${e.message}")
            e.printStackTrace()
        }
    }
}

