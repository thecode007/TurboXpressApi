package com.thecode007.turboxpress.controller

import com.thecode007.turboxpress.dto.*
import com.thecode007.turboxpress.service.RestaurantItemService
import com.thecode007.turboxpress.service.RestaurantService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/restaurants")
class RestaurantController(
    private val restaurantService: RestaurantService,
    private val restaurantItemService: RestaurantItemService
) {

    @GetMapping
    fun getAllRestaurants(): ResponseEntity<BaseResponse<List<RestaurantResponse>>> {
        val restaurants = restaurantService.getAllRestaurants()
        return ResponseEntity.ok(BaseResponse.success("Restaurants retrieved successfully", restaurants))
    }

    @GetMapping("/{id}")
    fun getRestaurantById(@PathVariable id: Long): ResponseEntity<BaseResponse<RestaurantResponse>> {
        val restaurant = restaurantService.getRestaurantById(id)
        return ResponseEntity.ok(BaseResponse.success("Restaurant retrieved successfully", restaurant))
    }

    @PostMapping
    fun createRestaurant(@Valid @RequestBody request: CreateRestaurantRequest): ResponseEntity<BaseResponse<RestaurantResponse>> {
        val restaurant = restaurantService.createRestaurant(request)
        return ResponseEntity.ok(BaseResponse.success("Restaurant created successfully", restaurant))
    }

    @PutMapping("/{id}")
    fun updateRestaurant(
        @PathVariable id: Long,
        @RequestBody request: UpdateRestaurantRequest
    ): ResponseEntity<BaseResponse<RestaurantResponse>> {
        val restaurant = restaurantService.updateRestaurant(id, request)
        return ResponseEntity.ok(BaseResponse.success("Restaurant updated successfully", restaurant))
    }

    @DeleteMapping("/{id}")
    fun deleteRestaurant(@PathVariable id: Long): ResponseEntity<BaseResponse<Unit>> {
        restaurantService.deleteRestaurant(id)
        return ResponseEntity.ok(BaseResponse.success("Restaurant deleted successfully", null))
    }

    @PostMapping("/{id}/items")
    fun addItemToRestaurant(
        @PathVariable id: Long,
        @Valid @RequestBody request: CreateRestaurantItemRequest
    ): ResponseEntity<BaseResponse<RestaurantItemResponse>> {
        val item = restaurantItemService.addItemToRestaurant(id, request)
        return ResponseEntity.ok(BaseResponse.success("Item added to restaurant successfully", item))
    }

    @PutMapping("/items/{itemId}")
    fun updateItem(
        @PathVariable itemId: Long,
        @RequestBody request: UpdateRestaurantItemRequest
    ): ResponseEntity<BaseResponse<RestaurantItemResponse>> {
        val item = restaurantItemService.updateItem(itemId, request)
        return ResponseEntity.ok(BaseResponse.success("Item updated successfully", item))
    }

    @DeleteMapping("/items/{itemId}")
    fun deleteItem(@PathVariable itemId: Long): ResponseEntity<BaseResponse<Unit>> {
        restaurantItemService.deleteItem(itemId)
        return ResponseEntity.ok(BaseResponse.success("Item deleted successfully", null))
    }
}
