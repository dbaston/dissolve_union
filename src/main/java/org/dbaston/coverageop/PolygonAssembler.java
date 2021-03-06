package org.dbaston.coverageop;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.index.strtree.STRtree;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author dbaston
 */
public class PolygonAssembler {
    /** addInteriorRing constructs a new Polygon using the shell and interior
     *  rings of p, plus an additional supplied interior ring.  It does not
     *  check that the resulting polygon is valid.
     * @param p An input Polygon, with zero or more interior rings
     * @param intring A Geometry representing an interior ring, either as a
     *                LinearRing, closed LineString, or Polygon with no
     *                interior rings.
     * @return A new Polygon that includes the shell and interior rings of
     *         p, plus the supplied additional interior ring.
     */
    public static Polygon addInteriorRing(Polygon p, Geometry intring) {
        GeometryFactory gfact = p.getFactory();
        LinearRing   shell = gfact.createLinearRing(p.getExteriorRing().getCoordinateSequence());
        LinearRing[] holes = new LinearRing[p.getNumInteriorRing() + 1];
       
        if (intring instanceof LinearRing) {
            holes[0] = (LinearRing) intring;
        }
        else if (intring instanceof LineString && ((LineString) intring).isClosed()) {
            holes[0] = gfact.createLinearRing(((LineString) intring).getCoordinateSequence());
        }
        else if (intring instanceof Polygon && ((Polygon) intring).getNumInteriorRing() == 0) {
            holes[0] = gfact.createLinearRing(((Polygon) intring).getExteriorRing().getCoordinateSequence());
        }
        else {
            throw new IllegalArgumentException("Supplied ring geometry must be a LinearRing, closed LineString, or a Polygon with no interior rings.");
        }

        for (int i = 1; i < p.getNumInteriorRing(); i++) {
            holes[i] = gfact.createLinearRing(p.getInteriorRingN(i).getCoordinateSequence());
        }
        
        return gfact.createPolygon(shell, holes);
    }

    public static Polygon[] getAssembled (Collection<LineString> rings) {
        if (rings.isEmpty()) {
            return new Polygon[0];
        }
        
        GeometryFactory gfact = rings.iterator().next().getFactory();
        
        Polygon[] polys = new Polygon[rings.size()];
        int i = 0;
        for (LineString l : rings) {
            polys[i] = gfact.createPolygon(l.getCoordinates());
            i++;
        }
        
        return getAssembled(polys);
    }
    
    public static Polygon[] getAssembled (Polygon[] polys) {
        // Sort the array so that, when iterating positively over the array,
        // polygon shells are encountered before their potential holes.
        Arrays.sort(polys, new Comparator<Geometry>() {
            @Override
            public int compare(Geometry a, Geometry b) {
                // If Geometry a could be contained within Geometry b
                // (ie, a is a hole within the shell of b), this function
                // must return 1 or 0.  The function may return 1 or 0
                // even if it is impossible that b is a hole within a.
                return Double.compare(a.getEnvelopeInternal().getMinX(),
                                      b.getEnvelopeInternal().getMinY());
                
                //return ((Double) b.getEnvelopeInternal().getArea()).compareTo(
                //        a.getEnvelopeInternal().getArea());
            }
        });
        
        // Build a spatial index on the rings (represented as polygons)
        STRtree polyIndex = new STRtree();
        for (int i = 0; i < polys.length; i++) {
            polyIndex.insert(polys[i].getEnvelopeInternal(), i);
        }

        int numPolys = polys.length;
        // Identify polygons that should be considered interior rings of other polygons
        for (int i = 0; i < polys.length; i++) {
            if (polys[i] == null) {
                // This polygon has already been identified as an interior ring
                // of something else...skip it.
                numPolys -= 1;
                continue;
            }
            
            List<Integer> potentialInteriorRings = polyIndex.query(polys[i].getEnvelopeInternal());
            
            // If multiple rings are within the exterior ring of a polygon,
            // the outermost ring needs to be located first.
            Collections.sort(potentialInteriorRings); 
                                                      
            for (int j : potentialInteriorRings) {
                if (i < j   // only j > i could be an interior ring of i, because an interior
                            // ring's envelope cannot have a minimum X coordinate greater than
                            // its containing shell
                        && polys[j] != null                // make sure this polygon is not already
                                                           // identified as an interior ring of
                                                           // something else.  what would appear to be
                                                           // an interior ring of an interior ring should
                                                           // be considered a separate exterior ring in a 
                                                           // separate polygon
                        && polys[j].getEnvelopeInternal().intersects(polys[i].getEnvelopeInternal()) // bbox isect
                        && polys[i].contains(polys[j])) { // ring is inside poly
                    
                    polys[i] = addInteriorRing(polys[i], polys[j]);
                    polys[j] = null;
                }
            }
        }
  
        Polygon[] polyArray = new Polygon[numPolys];
        {
            int i = 0;
            for (Polygon p : polys) {
                if (p != null) {
                    polyArray[i++] = p;
                }
            }
        }

        return polyArray;
    }
}
