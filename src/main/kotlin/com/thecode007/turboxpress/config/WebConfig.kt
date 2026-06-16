package com.thecode007.turboxpress.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig : WebMvcConfigurer {

    @Value("\${file.upload.dir}")
    private lateinit var uploadDir: String

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadPath = Paths.get(uploadDir).toFile().absolutePath
        registry.addResourceHandler("/api/media/profiles/**")
            .addResourceLocations("file:$uploadPath/")
    }
}
