package com.thecode007.turboxpress.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.IOException

@Configuration
class FirebaseConfig {

    @PostConstruct
    fun initialize() {
        try {
            val serviceAccount = ClassPathResource("firebase-service-account.json")
            
            if (!serviceAccount.exists()) {
                println("CRITICAL: Firebase service account file 'firebase-service-account.json' not found in resources!")
                return
            }

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount.inputStream))
                .setProjectId("turboxpress-b159c")
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options)
                println("Firebase has been initialized successfully.")
            } else {
                println("Firebase already initialized.")
            }
        } catch (e: IOException) {
            println("Error initializing Firebase: ${e.message}")
            e.printStackTrace()
        }
    }
}