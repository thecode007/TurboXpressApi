package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.service.MediaService
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/media")
class MediaController(
    private val mediaService: MediaService
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadFile(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<BaseResponse<String>> {
        val url = mediaService.uploadProfilePicture(file)
        return ResponseEntity.ok(BaseResponse.success("File uploaded successfully", url))
    }
    
    @GetMapping("/profiles/{filename:.+}")
    fun getFile(@PathVariable filename: String): ResponseEntity<Resource> {
        val url = "/uploads/profiles/$filename"
        val file = mediaService.getFilePath(url)
        val resource = UrlResource(file.toUri())
        
        return if (resource.exists() && resource.isReadable) {
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${resource.filename}\"")
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
