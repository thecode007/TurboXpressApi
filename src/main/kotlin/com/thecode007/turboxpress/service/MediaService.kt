package com.thecode007.turboxpress.service

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class MediaService {
    
    @Value("\${file.upload.dir}")
    private lateinit var uploadDir: String
    
    @PostConstruct
    fun init() {
        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(uploadDir))
        } catch (e: IOException) {
            throw RuntimeException("Could not create upload directory!", e)
        }
    }
    
    fun uploadProfilePicture(file: MultipartFile): String {
        if (file.isEmpty) {
            throw IllegalArgumentException("File is empty")
        }
        
        // Validate file type
        val contentType = file.contentType
        if (contentType == null || !contentType.startsWith("image/")) {
            throw IllegalArgumentException("Only image files are allowed")
        }
        
        // Generate unique filename
        val originalFilename = file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast(".", "jpg")
        val uniqueFilename = "${UUID.randomUUID()}.$extension"
        
        // Save file
        val targetLocation = Paths.get(uploadDir).resolve(uniqueFilename)
        Files.copy(file.inputStream, targetLocation, StandardCopyOption.REPLACE_EXISTING)
        
        // Return URL path
        return "/uploads/profiles/$uniqueFilename"
    }
    
    fun deleteProfilePicture(url: String) {
        if (url.isBlank()) return
        
        try {
            // Extract filename from URL
            val filename = url.substringAfterLast("/")
            val filePath = Paths.get(uploadDir).resolve(filename)
            
            // Delete file if exists
            Files.deleteIfExists(filePath)
        } catch (e: IOException) {
            // Log error but don't throw - file might already be deleted
            println("Error deleting file: ${e.message}")
        }
    }
    
    fun getFilePath(url: String): Path {
        val filename = url.substringAfterLast("/")
        return Paths.get(uploadDir).resolve(filename)
    }
}
