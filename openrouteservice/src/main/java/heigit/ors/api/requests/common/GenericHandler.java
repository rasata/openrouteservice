/*
 * This file is part of Openrouteservice.
 *
 * Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, see <https://www.gnu.org/licenses/>.
 */

package heigit.ors.api.requests.common;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import heigit.ors.api.requests.routing.RequestProfileParamsRestrictions;
import heigit.ors.api.requests.routing.RequestProfileParamsWeightings;
import heigit.ors.api.requests.routing.RouteRequest;
import heigit.ors.exceptions.IncompatableParameterException;
import heigit.ors.exceptions.ParameterValueException;
import heigit.ors.exceptions.StatusCodeException;
import heigit.ors.exceptions.UnknownParameterValueException;
import heigit.ors.geojson.GeometryJSON;
import heigit.ors.routing.AvoidFeatureFlags;
import heigit.ors.routing.ProfileWeighting;
import heigit.ors.routing.RoutingProfileType;
import heigit.ors.routing.graphhopper.extensions.HeavyVehicleAttributes;
import heigit.ors.routing.graphhopper.extensions.VehicleLoadCharacteristicsFlags;
import heigit.ors.routing.graphhopper.extensions.WheelchairTypesEncoder;
import heigit.ors.routing.parameters.*;
import heigit.ors.routing.pathprocessors.BordersExtractor;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericHandler {
    protected Map<String, Integer> errorCodes;

    public GenericHandler() {
        errorCodes = new HashMap<>();
    }

    protected String[] convertAPIEnumListToStrings(Enum[] valuesIn) {
        String[] attributes = new String[valuesIn.length];
        for(int i=0; i<valuesIn.length; i++) {
            attributes[i] = convertAPIEnum(valuesIn[i]);
        }

        return attributes;
    }

    protected String convertAPIEnum(Enum valuesIn) {
        return valuesIn.toString();
    }

    protected int convertVehicleType(APIEnums.VehicleType vehicleTypeIn, int profileType) throws IncompatableParameterException {
        if(!RoutingProfileType.isHeavyVehicle(profileType)) {
            throw new IncompatableParameterException(getErrorCode("INVALID_PARAMETER_VALUE"),
                    "vehicle_type", vehicleTypeIn.toString(),
                    "profile", RoutingProfileType.getName(profileType));
        }
        if(vehicleTypeIn == null) {
            return HeavyVehicleAttributes.UNKNOWN;
        }

        return HeavyVehicleAttributes.getFromString(vehicleTypeIn.toString());
    }

    private Integer getErrorCode(String name) {
        int errorCode = -1;
        if(errorCodes.containsKey(name)) {
            errorCode = errorCodes.get(name);
        }

        return errorCode;
    }

    protected BordersExtractor.Avoid convertAvoidBorders(APIEnums.AvoidBorders avoidBorders) {
        if(avoidBorders != null) {
            switch (avoidBorders) {
                case ALL:
                    return BordersExtractor.Avoid.ALL;
                case CONTROLLED:
                    return BordersExtractor.Avoid.CONTROLLED;
                default:
                    return BordersExtractor.Avoid.NONE;
            }
        }
        return null;
    }

    protected int convertRouteProfileType(APIEnums.RoutingProfile profile) {
        return RoutingProfileType.getFromString(profile.toString());
    }

    protected Polygon[] convertAvoidAreas(JSONObject geoJson) throws ParameterValueException {
        // It seems that arrays in json.simple cannot be converted to strings simply
        org.json.JSONObject complexJson = new org.json.JSONObject();
        complexJson.put("type", geoJson.get("type"));
        List<List<Double[]>> coordinates = (List<List<Double[]>>) geoJson.get("coordinates");
        complexJson.put("coordinates", coordinates);

        Geometry convertedGeom;
        try {
            convertedGeom = GeometryJSON.parse(complexJson);
        } catch (Exception e) {
            throw new ParameterValueException(getErrorCode("INVALID_JSON_FORMAT"), "avoid_polygons");
        }

        Polygon[] avoidAreas;

        if (convertedGeom instanceof Polygon) {
            avoidAreas = new Polygon[]{(Polygon) convertedGeom};
        } else if (convertedGeom instanceof MultiPolygon) {
            MultiPolygon multiPoly = (MultiPolygon) convertedGeom;
            avoidAreas = new Polygon[multiPoly.getNumGeometries()];
            for (int i = 0; i < multiPoly.getNumGeometries(); i++)
                avoidAreas[i] = (Polygon) multiPoly.getGeometryN(i);
        } else {
            throw new ParameterValueException(getErrorCode("INVALID_PARAMETER_VALUE"), "avoid_polygons");
        }

        return avoidAreas;
    }

    protected int convertFeatureTypes(APIEnums.AvoidFeatures[] avoidFeatures, int profileType) throws UnknownParameterValueException, IncompatableParameterException {
        int flags = 0;
        for(APIEnums.AvoidFeatures avoid : avoidFeatures) {
            String avoidFeatureName = avoid.toString();
            int flag = AvoidFeatureFlags.getFromString(avoidFeatureName);
            if(flag == 0)
                throw new UnknownParameterValueException(getErrorCode("INVALID_PARAMETER_VALUE"), "avoid_features", avoidFeatureName);

            if (!AvoidFeatureFlags.isValid(profileType, flag, avoidFeatureName))
                throw new IncompatableParameterException(getErrorCode("INVALID_PARAMETER_VALUE"), "avoid_features", avoidFeatureName, "profile", RoutingProfileType.getName(profileType));

            flags |= flag;
        }

        return flags;
    }

    protected ProfileParameters convertParameters(RouteRequest request, int profileType) throws StatusCodeException {
        ProfileParameters params = new ProfileParameters();

        if(request.getRouteOptions().getProfileParams().hasRestrictions()) {

            RequestProfileParamsRestrictions restrictions = request.getRouteOptions().getProfileParams().getRestrictions();
            APIEnums.VehicleType vehicleType = request.getRouteOptions().getVehicleType();

            validateRestrictionsForProfile(restrictions, profileType);

            if (RoutingProfileType.isCycling(profileType))
                params = convertCyclingParameters(restrictions);
            if (RoutingProfileType.isHeavyVehicle(profileType))
                params = convertHeavyVehicleParameters(restrictions, vehicleType);
            if (RoutingProfileType.isWalking(profileType))
                params = convertWalkingParameters(restrictions);
            if (RoutingProfileType.isWheelchair(profileType))
                params = convertWheelchairParameters(restrictions);
        }

        if(request.getRouteOptions().getProfileParams().hasWeightings()) {
            RequestProfileParamsWeightings weightings = request.getRouteOptions().getProfileParams().getWeightings();
            applyWeightings(weightings, params);
        }

        return params;
    }

    private CyclingParameters convertCyclingParameters(RequestProfileParamsRestrictions restrictions) {

        CyclingParameters params = new CyclingParameters();
        if(restrictions.hasGradient())
            params.setMaximumGradient(restrictions.getGradient());
        if(restrictions.hasTrailDifficulty())
            params.setMaximumTrailDifficulty(restrictions.getTrailDifficulty());

        return params;
    }

    private WalkingParameters convertWalkingParameters(RequestProfileParamsRestrictions restrictions) {

        WalkingParameters params = new WalkingParameters();
        if(restrictions.hasGradient())
            params.setMaximumGradient(restrictions.getGradient());
        if(restrictions.hasTrailDifficulty())
            params.setMaximumTrailDifficulty(restrictions.getTrailDifficulty());

        return params;
    }

    private VehicleParameters convertHeavyVehicleParameters(RequestProfileParamsRestrictions restrictions, APIEnums.VehicleType vehicleType) {

        VehicleParameters params = new VehicleParameters();
        if(vehicleType != null && vehicleType != APIEnums.VehicleType.UNKNOWN) {
            if(restrictions.hasLength())
                params.setLength(restrictions.getLength());
            if(restrictions.hasWidth())
                params.setWidth(restrictions.getWidth());
            if(restrictions.hasHeight())
                params.setHeight(restrictions.getHeight());
            if(restrictions.hasWeight())
                params.setWeight(restrictions.getWeight());
            if(restrictions.hasAxleLoad())
                params.setAxleload(restrictions.getAxleLoad());

            int loadCharacteristics = 0;
            if(restrictions.hasHazardousMaterial() && restrictions.getHazardousMaterial() == true)
                loadCharacteristics |= VehicleLoadCharacteristicsFlags.HAZMAT;

            if(loadCharacteristics != 0)
                params.setLoadCharacteristics(loadCharacteristics);
        }

        return params;
    }

    private WheelchairParameters convertWheelchairParameters(RequestProfileParamsRestrictions restrictions) {

        WheelchairParameters params = new WheelchairParameters();

        if(restrictions.hasSurfaceType())
            params.setSurfaceType(WheelchairTypesEncoder.getSurfaceType(restrictions.getSurfaceType()));
        if(restrictions.hasTrackType())
            params.setTrackType(WheelchairTypesEncoder.getTrackType(restrictions.getTrackType()));
        if(restrictions.hasSmoothnessType())
            params.setSmoothnessType(WheelchairTypesEncoder.getSmoothnessType(restrictions.getSmoothnessType()));
        if(restrictions.hasMaxSlopedKerb())
            params.setMaximumSlopedKerb(restrictions.getMaxSlopedKerb());
        if(restrictions.hasMaxIncline())
            params.setMaximumIncline(restrictions.getMaxIncline());
        if(restrictions.hasMinWidth())
            params.setMinimumWidth(restrictions.getMinWidth());

        return params;
    }

    private  void validateRestrictionsForProfile(RequestProfileParamsRestrictions restrictions, int profile) throws IncompatableParameterException {
        // Check that we do not have some parameters that should not be there
        List<String> setRestrictions = restrictions.getSetRestrictions();
        ProfileParameters params = new ProfileParameters();
        if(RoutingProfileType.isCycling(profile)) {
            params = new CyclingParameters();
        }
        if(RoutingProfileType.isWheelchair(profile)) {
            params = new WheelchairParameters();
        }
        if(RoutingProfileType.isWalking(profile)) {
            params = new WalkingParameters();
        }
        if(RoutingProfileType.isHeavyVehicle(profile)) {
            params = new VehicleParameters();
        }

        List<String> invalidParams = new ArrayList<>();
        for(String setRestriction : setRestrictions) {
            boolean valid = false;
            for(String validRestriction : params.getValidRestrictions()) {
                if(validRestriction.equals(setRestriction)) {
                    valid = true;
                    break;
                }
            }

            if(!valid) {
                invalidParams.add(setRestriction);
            }
        }

        if(invalidParams.size() > 0) {
            // There are some parameters present that shouldn't be there
            String invalidParamsString = StringUtils.join(invalidParams, ", ");
            throw new IncompatableParameterException(getErrorCode("UNKNOWN_PARAMETER"), "restrictions", invalidParamsString, "profile", RoutingProfileType.getName(profile));
        }
    }

    private  ProfileParameters applyWeightings(RequestProfileParamsWeightings weightings, ProfileParameters params) {
        try {
            if (weightings.hasGreenIndex()) {
                ProfileWeighting pw = new ProfileWeighting("green");
                pw.addParameter("factor", String.format("%.2f", weightings.getGreenIndex()));
                params.add(pw);
            }
            if(weightings.hasQuietIndex()) {
                ProfileWeighting pw = new ProfileWeighting("quiet");
                pw.addParameter("factor", String.format("%.2f", weightings.getQuietIndex()));
                params.add(pw);
            }
            if(weightings.hasSteepnessDifficulty()) {
                ProfileWeighting pw = new ProfileWeighting("steepness_difficulty");
                pw.addParameter("level", String.format("%d", weightings.getSteepnessDifficulty()));
                params.add(pw);
            }
        } catch (Exception e) {

        }

        return params;
    }




}