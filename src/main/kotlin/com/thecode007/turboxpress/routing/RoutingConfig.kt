package com.thecode007.turboxpress.routing

import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RoutingConfig {

    @Bean
    fun graphHopper(): GraphHopper {
        val graphHopper = GraphHopper()
        // Configure it to read map data from src/main/resources/maps/region.osm.pbf
        graphHopper.setOSMFile("src/main/resources/maps/region.osm.pbf")
        // Set the graph cache location
        graphHopper.setGraphHopperLocation("src/main/resources/maps/routing-graph-cache")
        
        // Initialize standard "car" profile programmatically to bypass the Jackson parsing bug
        // We use the fully qualified class names to ensure compatibility with both GH 9.0 and 11.0
        val customModel = com.graphhopper.util.CustomModel()
        customModel.addToPriority(com.graphhopper.json.Statement.If("!car_access", com.graphhopper.json.Statement.Op.MULTIPLY, "0"))
        customModel.addToSpeed(com.graphhopper.json.Statement.If("true", com.graphhopper.json.Statement.Op.LIMIT, "car_average_speed"))
        
        graphHopper.setProfiles(Profile("car").setCustomModel(customModel))
        
        // GraphHopper 9.0 requires explicitly defining which encoded values to load
        graphHopper.setEncodedValuesString("car_access, car_average_speed")
        
        graphHopper.importOrLoad()
        
        return graphHopper
    }
}
