package org.dbaston.coverageop;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** A DuplicateSegmentFinder extracts the LineSegments that make up a set of
 * supplied Polygon or MultiPolygon geometries, and provides methods to 
 * retrieve only the segments that are or are not unique.  Currently, the class
 * assumes that a segment will be shared by at most two input features, and will
 * return incorrect results if this condition is not met.
 * @author dbaston
 */
public class DuplicateSegmentFinder {
    protected HashSet<LineSegment> lines;
	protected HashSet<LineSegment> duplicateLines;
	private boolean retainUnique;
    
    public DuplicateSegmentFinder(boolean retainUnique) {
        lines = new HashSet<>(); 
    }
    
    public DuplicateSegmentFinder(Collection<Geometry> geoms, boolean retainUnique) {
        int numPoints = 0;
		for (Geometry g : geoms) {
	   		numPoints += g.getNumPoints();
		} 
	    
		lines = new HashSet(numPoints);
		duplicateLines = new HashSet(numPoints);
        for (Geometry g : geoms) {
            add(g);
        }
    }
    
	public Set<LineSegment> getUniqueSegments() {
		return lines;
	}

	public Set<LineSegment> getDuplicateSegments() {
		return duplicateLines;
	}
	
	protected void processSegment(LineSegment ls) {
		ls.normalize();
		if (!lines.remove(ls)) {
			lines.add(ls);
		} else if (this.retainUnique) {
			duplicateLines.add(ls);
		}
	}
	
    /** add extracts the Segments that make up a geometry, and adds them to
     *  the hash of Segments.
     *  @param geom the Polygon or MultiPolygon to be added
     */
    protected void add (Geometry g) {
        if (g instanceof MultiPolygon) {
            for (int i = 0; i < g.getNumGeometries(); i++) {
                add((Polygon) g.getGeometryN(i));
            }
            return;
        }
        
        if (!(g instanceof Polygon)) {
            throw new IllegalArgumentException("Geometries must be Polygons or MultiPolygons");
        }
        
        Polygon p = (Polygon) g;
        Coordinate[][] rings = new Coordinate[1 + p.getNumInteriorRing()][];
        
        // Populate the array of ring coordinates
        rings[0] = p.getExteriorRing().getCoordinates();
        for (int i = 0; i < p.getNumInteriorRing(); i++) {
            rings[i+1] = p.getInteriorRingN(i).getCoordinates();
        }
            
        // Loop over the aray of ring coordinates, constructing LineSegments
        // and checking them for uniqueness
        for (int i = 0; i < rings.length; i++) {
            for (int j = 0; j < rings[i].length - 1; j++) {
                LineSegment ls = new LineSegment (rings[i][j], rings[i][j+1]);
                processSegment(ls);
            }
        }
    }
    
	/** getUniqueLineSegments converts the collection of LineSegment
     *  objects into LineStrings using the supplied GeometryFactory.
	 *  @param gfact a GeometryFactory to use for creating LineStrings
	 *  @return Array of LineString objects, with one LineString per
	 *          LineSegment.
	 */
	public LineString[] getUniqueLineStrings(GeometryFactory gfact) {
		LineString[] linestrings = new LineString[lines.size()];
		int i = 0;
		for (LineSegment l : getUniqueSegments()) {
            linestrings[i++] = l.toGeometry(gfact);
        }
		return linestrings;
	}
	
	/** getUniqueMultiLineString converts the collection of LineSegment objects
  into a MultiLineString using the supplied GeometryFactory.
	 *  @param gfact a GeometryFactory to use for creating LineStrings
	 *  @return MultiLineString with one LineString per LineSegment.
	 */
	public MultiLineString getUniqueMultiLineString(GeometryFactory gfact) {
		return gfact.createMultiLineString(getUniqueLineStrings(gfact));
	}
	
    /** getUniqueMergedLineSegments converts the supplied collection of 
	 * LineSegment objects into LineStrings using the supplied GeometryFactory,
	 * and merges the resultant LineStrings using a LineMerger.  The returned
     *  LineStrings may or may not form closed rings.
     * @param gfact
     * @return Collection of LineString objects.
     */
    public Collection<LineString> getUniqueMergedLineSegments(GeometryFactory gfact) {
        LineMerger lm = new LineMerger();
        for (LineSegment l : getUniqueSegments()) {
            lm.add(l.toGeometry(gfact));
        }
        return lm.getMergedLineStrings();    
    }
}
