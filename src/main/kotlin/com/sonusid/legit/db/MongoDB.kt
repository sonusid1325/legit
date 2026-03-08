package com.sonusid.legit.db

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.sonusid.legit.models.Document
import com.sonusid.legit.models.User
import com.sonusid.legit.models.VerificationContract
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.kotlinx.KotlinSerializerCodecProvider
import org.bson.codecs.pojo.PojoCodecProvider

object MongoDB {

    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase

    lateinit var users: MongoCollection<User>
        private set

    lateinit var documents: MongoCollection<Document>
        private set

    lateinit var contracts: MongoCollection<VerificationContract>
        private set

    fun init(application: Application) {
        val mongoUri = application.environment.config
            .propertyOrNull("mongodb.uri")?.getString()
            ?: "mongodb://localhost:27017"

        val databaseName = application.environment.config
            .propertyOrNull("mongodb.database")?.getString()
            ?: "legit"

        val kotlinSerializerCodecProvider = KotlinSerializerCodecProvider()

        val codecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(
                kotlinSerializerCodecProvider,
                PojoCodecProvider.builder().automatic(true).build()
            )
        )

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(mongoUri))
            .codecRegistry(codecRegistry)
            .build()

        client = MongoClient.create(settings)
        database = client.getDatabase(databaseName)

        users = database.getCollection<User>("users")
        documents = database.getCollection<Document>("documents")
        contracts = database.getCollection<VerificationContract>("contracts")

        runBlocking {
            createIndexes()
        }

        application.log.info("MongoDB connected to database: $databaseName")

        application.monitor.subscribe(ApplicationStopped) {
            close()
        }
    }

    private suspend fun createIndexes() {
        // User indexes
        users.createIndex(
            Indexes.ascending("email"),
            IndexOptions().unique(true)
        )
        users.createIndex(
            Indexes.ascending("username"),
            IndexOptions().unique(true)
        )
        users.createIndex(Indexes.ascending("phoneNumber"))

        // Document indexes
        documents.createIndex(Indexes.ascending("userId"))
        documents.createIndex(Indexes.ascending("documentType"))
        documents.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("documentType"),
                Indexes.ascending("documentNumber")
            ),
            IndexOptions().unique(true)
        )
        documents.createIndex(Indexes.ascending("status"))
        documents.createIndex(Indexes.ascending("dataHash"))

        // Contract indexes
        contracts.createIndex(Indexes.ascending("userId"))
        contracts.createIndex(Indexes.ascending("requesterId"))
        contracts.createIndex(Indexes.ascending("status"))
        contracts.createIndex(Indexes.ascending("disposableKey"))
        contracts.createIndex(Indexes.ascending("expiresAt"))
        contracts.createIndex(
            Indexes.compoundIndex(
                Indexes.ascending("userId"),
                Indexes.ascending("status")
            )
        )
    }

    fun close() {
        if (::client.isInitialized) {
            client.close()
        }
    }

    fun isInitialized(): Boolean = ::database.isInitialized
}
