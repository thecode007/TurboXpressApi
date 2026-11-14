package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.BaseResponse
import com.thecode007.turboxpress.dto.CreateTerritoryRequest
import com.thecode007.turboxpress.dto.TerritoryResponse
import com.thecode007.turboxpress.dto.UpdateTerritoryRequest
import com.thecode007.turboxpress.service.TerritoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/territories")
@Tag(name = "Territory Management", description = "Endpoints for managing operational territories for users, drivers, and restaurants")
@SecurityRequirement(name = "Bearer Authentication")
class TerritoryController(
    private val territoryService: TerritoryService
) {

    @GetMapping
    @Operation(summary = "Get all territories", description = "Retrieves a list of all territories")
    fun getAllTerritories(): ResponseEntity<BaseResponse<List<TerritoryResponse>>> {
        val territories = territoryService.getAllTerritories()
        return ResponseEntity.ok(
            BaseResponse.success(
                message = "Territories retrieved successfully",
                data = territories
            )
        )
    }

    @GetMapping("/resolve")
    @Operation(
        summary = "Resolve territory by coordinates",
        description = "Calculates and returns the active territory containing the given (latitude, longitude) point, or null if outside all territories"
    )
    fun resolveTerritory(
        @RequestParam latitude: Double,
        @RequestParam longitude: Double
    ): ResponseEntity<BaseResponse<TerritoryResponse?>> {
        val territory = territoryService.resolveTerritoryResponseForCoordinates(latitude, longitude)
        val message = if (territory != null) {
            "Point belongs to territory '${territory.name}'"
        } else {
            "Point is not inside any active territory"
        }
        return ResponseEntity.ok(
            BaseResponse.success(
                message = message,
                data = territory
            )
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get territory by ID", description = "Retrieves a specific territory by its ID")
    fun getTerritoryById(@PathVariable id: Long): ResponseEntity<BaseResponse<TerritoryResponse>> {
        val territory = territoryService.getTerritoryById(id)
        return ResponseEntity.ok(
            BaseResponse.success(
                message = "Territory retrieved successfully",
                data = territory
            )
        )
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create territory", description = "Creates a new territory with a WKT polygon and recalculates restaurant territories")
    fun createTerritory(
        @Valid @RequestBody request: CreateTerritoryRequest
    ): ResponseEntity<BaseResponse<TerritoryResponse>> {
        val created = territoryService.createTerritory(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            BaseResponse.success(
                message = "Territory created successfully",
                data = created
            )
        )
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update territory", description = "Updates an existing territory and recalculates restaurant territories")
    fun updateTerritory(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateTerritoryRequest
    ): ResponseEntity<BaseResponse<TerritoryResponse>> {
        val updated = territoryService.updateTerritory(id, request)
        return ResponseEntity.ok(
            BaseResponse.success(
                message = "Territory updated successfully",
                data = updated
            )
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete territory", description = "Deletes a territory by ID and recalculates restaurant territories")
    fun deleteTerritory(@PathVariable id: Long): ResponseEntity<BaseResponse<Nothing>> {
        territoryService.deleteTerritory(id)
        return ResponseEntity.ok(
            BaseResponse.success(message = "Territory deleted successfully")
        )
    }
}
