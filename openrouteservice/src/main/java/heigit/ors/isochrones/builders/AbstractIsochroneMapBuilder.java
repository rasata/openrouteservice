/*  This file is part of Openrouteservice.
 *
 *  Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1 
 *  of the License, or (at your option) any later version.

 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details.

 *  You should have received a copy of the GNU Lesser General Public License along with this library; 
 *  if not, see <https://www.gnu.org/licenses/>.  
 */
package heigit.ors.isochrones.builders;

import heigit.ors.isochrones.builders.IsochroneMapBuilder;
import heigit.ors.routing.RouteSearchContext;
import heigit.ors.isochrones.IsochroneSearchParameters;
import heigit.ors.isochrones.IsochroneMap;

public abstract class AbstractIsochroneMapBuilder implements IsochroneMapBuilder {
	
	public abstract void initialize(RouteSearchContext searchContext);
	
	public abstract IsochroneMap compute(IsochroneSearchParameters parameters) throws Exception; 
}
