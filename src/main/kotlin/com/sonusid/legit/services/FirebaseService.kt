package com.sonusid.legit.services

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream

object FirebaseService {
    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)
    private var initialized = false

    fun isInitialized(): Boolean = initialized

    fun init() {
        try {
            val envPath = System.getenv("FIREBASE_SERVICE_ACCOUNT")
            val defaultPath = "service_provider_creds.json"
            val altPath = "legit/service_provider_creds.json"
            
            val file = when {
                envPath != null && File(envPath).exists() -> File(envPath)
                File(defaultPath).exists() -> File(defaultPath)
                File(altPath).exists() -> File(altPath)
                else -> null
            }
            
            if (file != null) {
                logger.info("Initializing Firebase with service account from: ${file.absolutePath}")
                val serviceAccount = FileInputStream(file)
                val options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build()

                FirebaseApp.initializeApp(options)
                initialized = true
                logger.info("Firebase Admin SDK initialized successfully.")
            } else {
                logger.warn("Firebase service account file not found (tried $envPath, $defaultPath, $altPath). Push notifications will be disabled (logged only).")
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase Admin SDK", e)
        }
    }

    fun sendPushNotification(token: String, title: String, body: String, data: Map<String, String> = emptyMap()) {
        if (!initialized) {
            logger.info("PUSH SIMULATION (Not Initialized): Token: $token, Title: $title, Body: $body, Data: $data")
            return
        }

        if (token.isBlank()) {
            logger.warn("Cannot send push notification: FCM token is blank.")
            return
        }

        try {
            logger.info("Attempting to send FCM notification to token: ${token.take(10)}...")
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("Successfully sent FCM message. Response: $response")
        } catch (e: Exception) {
            logger.error("Error sending push notification to token $token", e)
        }
    }
}
