package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateRestaurantRequest
import com.thecode007.turboxpress.dto.RestaurantResponse
import com.thecode007.turboxpress.dto.UpdateRestaurantRequest
import com.thecode007.turboxpress.entity.Restaurant
import com.thecode007.turboxpress.repository.OwnerRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantService(
    private val restaurantRepository: RestaurantRepository,
    private val ownerRepository: OwnerRepository
) {
    private val geometryFactory = GeometryFactory()

    fun getAllRestaurants(): List<RestaurantResponse> {
        return restaurantRepository.findAll().map { RestaurantResponse.from(it) }
    }

    fun getRestaurantById(id: Long): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $id") }
        return RestaurantResponse.from(restaurant)
    }

    @Transactional
    fun createRestaurant(request: CreateRestaurantRequest): RestaurantResponse {
        if (restaurantRepository.existsByName(request.name)) {
            throw IllegalArgumentException("A restaurant with name '${request.name}' already exists")
        }

        val owner = ownerRepository.findById(request.ownerPhoneNumber)
            .orElseThrow { IllegalArgumentException("Owner not found with phone number: ${request.ownerPhoneNumber}") }

        val location = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        
        val restaurant = Restaurant(
            name = request.name,
            logoUrl = request.logoUrl,
            location = location,
            owner = owner
        )

        return RestaurantResponse.from(restaurantRepository.save(restaurant))
    }

    @Transactional
    fun updateRestaurant(id: Long, request: UpdateRestaurantRequest): RestaurantResponse {
        val restaurant = restaurantRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $id") }

        if (request.name != null && restaurantRepository.existsByNameAndIdNot(request.name, id)) {
            throw IllegalArgumentException("A restaurant with name '${request.name}' already exists")
        }

        request.name?.let { restaurant.name = it }
        request.logoUrl?.let { restaurant.logoUrl = it }
        
        if (request.latitude != null && request.longitude != null) {
            restaurant.location = geometryFactory.createPoint(Coordinate(request.longitude, request.latitude))
        }

        request.ownerPhoneNumber?.let {
            val owner = ownerRepository.findById(it)
                .orElseThrow { IllegalArgumentException("Owner not found with phone number: $it") }
            restaurant.owner = owner
        }

        return RestaurantResponse.from(restaurantRepository.save(restaurant))
    }

    @Transactional
    fun deleteRestaurant(id: Long) {
        if (!restaurantRepository.existsById(id)) {
            throw IllegalArgumentException("Restaurant not found with id: $id")
        }
        restaurantRepository.deleteById(id)
    }
}
