//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2010 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.tudey.shape;

import org.lwjgl.opengl.GL11;

import com.samskivert.util.StringUtil;

import com.threerings.math.FloatMath;
import com.threerings.math.Ray2D;
import com.threerings.math.Rect;
import com.threerings.math.Transform2D;
import com.threerings.math.Vector2f;

import com.threerings.tudey.shape.config.ShapeConfig;
import com.threerings.tudey.space.SpaceElement;

import static com.threerings.tudey.Log.*;

/**
 * A convex polygon.
 */
public class Polygon extends Shape
{
    /**
     * Creates a polygon with the supplied vertices.
     */
    public Polygon (Vector2f... vertices)
    {
        _vertices = new Vector2f[vertices.length];
        for (int ii = 0; ii < vertices.length; ii++) {
            _vertices[ii] = new Vector2f(vertices[ii]);
        }
        updateBounds();
    }

    /**
     * Creates an uninitialized polygon with the specified number of vertices.
     */
    public Polygon (int vcount)
    {
        initVertices(vcount);
    }

    /**
     * Returns the number of vertices in this polygon.
     */
    public int getVertexCount ()
    {
        return _vertices.length;
    }

    /**
     * Returns a reference to the indexed vertex.
     */
    public Vector2f getVertex (int idx)
    {
        return _vertices[idx];
    }

    /**
     * Checks whether the polygon contains the specified point.
     */
    public boolean contains (Vector2f pt)
    {
        return contains(pt.x, pt.y);
    }

    /**
     * Checks whether the polygon contains the specified point.
     */
    public boolean contains (float x, float y)
    {
        // check the point against each edge
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            if (a*x + b*y < a*start.x + b*start.y) {
                return false;
            }
        }
        return true;
    }

    @Override // documentation inherited
    public void updateBounds ()
    {
        _bounds.fromPoints(_vertices);
    }

    @Override // documentation inherited
    public Vector2f getCenter (Vector2f result)
    {
        result.set(0f, 0f);
        for (Vector2f vertex : _vertices) {
            result.addLocal(vertex);
        }
        return result.multLocal(1f / _vertices.length);
    }

    @Override // documentation inherited
    public Shape transform (Transform2D transform, Shape result)
    {
        Polygon presult = (result instanceof Polygon) ?
            ((Polygon)result) : new Polygon(_vertices.length);
        if (presult.getVertexCount() != _vertices.length) {
            presult.initVertices(_vertices.length);
        }
        for (int ii = 0; ii < _vertices.length; ii++) {
            transform.transformPoint(_vertices[ii], presult._vertices[ii]);
        }
        presult.updateBounds();
        return presult;
    }

    @Override // documentation inherited
    public Shape expand (float amount, Shape result)
    {
        Polygon presult = (result instanceof Polygon) ?
            ((Polygon)result) : new Polygon(_vertices.length);
        if (presult.getVertexCount() != _vertices.length) {
            presult.initVertices(_vertices.length);
        }
        // first compute the vertex normals and store them in the result
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f current = _vertices[ii];
            Vector2f next = _vertices[(ii + 1) % _vertices.length];
            Vector2f prev = _vertices[(ii + _vertices.length - 1) % _vertices.length];
            float nx = next.y - current.y, ny = current.x - next.x;
            float nl = FloatMath.hypot(nx, ny);
            float px = current.y - prev.y, py = prev.x - current.x;
            float pl = FloatMath.hypot(px, py);
            if (nl < FloatMath.EPSILON) {
                if (pl < FloatMath.EPSILON) {
                    presult._vertices[ii].set(Vector2f.ZERO);
                } else {
                    presult._vertices[ii].set(px, py).multLocal(1f / pl);
                }
            } else {
                if (pl < FloatMath.EPSILON) {
                    presult._vertices[ii].set(nx, ny).multLocal(1f / nl);
                } else {
                    float rnl = 1f / nl, rpl = 1f / pl;
                    presult._vertices[ii].set(nx*rnl + px*rpl, ny*rnl + py*rpl).normalizeLocal();
                }
            }
        }
        // then add the scaled normals to the vertices
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f rvert = presult._vertices[ii];
            _vertices[ii].addScaled(rvert, amount, rvert);
        }
        presult.updateBounds();
        return presult;
    }

    @Override // documentation inherited
    public Shape sweep (Vector2f translation, Shape result)
    {
        // TODO: may require computing the convex hull
        return new Polygon(_vertices);
    }

    @Override // documentation inherited
    public Vector2f[] getPerimeterPath ()
    {
        Vector2f[] path = new Vector2f[_vertices.length + 1];
        for (int ii = 0; ii < _vertices.length; ii++) {
            path[ii] = new Vector2f(_vertices[ii]);
        }
        path[_vertices.length] = new Vector2f(_vertices[0]);
        return path;
    }

    @Override // documentation inherited
    public boolean getIntersection (Ray2D ray, Vector2f result)
    {
        // see if we start inside the polygon
        Vector2f origin = ray.getOrigin();
        if (contains(origin)) {
            result.set(origin);
            return true;
        }
        // check the ray against each edge (making sure it's on the right side)
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            if (a*origin.x + b*origin.y <= a*start.x + b*start.y &&
                    ray.getIntersection(start, end, result)) {
                return true;
            }
        }
        return false;
    }

    @Override // documentation inherited
    public void getNearestPoint (Vector2f point, Vector2f result)
    {
        if (contains(point)) {
            result.set(point);
            return;
        }
        Vector2f currentResult = new Vector2f();
        float minDist = Float.MAX_VALUE;
        float dist;
        // find the nearest point to each edge
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            nearestPointOnSegment(start, end, point, currentResult);
            dist = point.distanceSquared(currentResult);
            if (dist < minDist) {
                minDist = dist;
                result.set(currentResult);
                if (Math.abs(minDist) < FloatMath.EPSILON) {
                    return;
                }
            }
        }
    }

    @Override // documentation inherited
    public IntersectionType getIntersectionType (Rect rect)
    {
        // make sure the bounds intersect (this is equivalent to doing a separating axis test
        // using the axes of the rectangle)
        if (!_bounds.intersects(rect)) {
            return IntersectionType.NONE;
        }

        // consider each edge of this polygon as a potential separating axis
        int ccount = 0;
        Vector2f rmin = rect.getMinimumExtent(), rmax = rect.getMaximumExtent();
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            float c = a*start.x + b*start.y;

            // determine how many vertices fall inside/outside the edge
            int inside =
                (a*rmin.x + b*rmin.y >= c ? 1 : 0) +
                (a*rmax.x + b*rmin.y >= c ? 1 : 0) +
                (a*rmax.x + b*rmax.y >= c ? 1 : 0) +
                (a*rmin.x + b*rmax.y >= c ? 1 : 0);
            if (inside == 0) {
                return IntersectionType.NONE;
            } else if (inside == 4) {
                ccount++;
            }
        }
        return (ccount == _vertices.length) ?
            IntersectionType.CONTAINS : IntersectionType.INTERSECTS;
    }

    @Override // documentation inherited
    public boolean intersects (SpaceElement element)
    {
        return element.intersects(this);
    }

    @Override // documentation inherited
    public boolean intersects (Shape shape)
    {
        return shape.intersects(this);
    }

    @Override // documentation inherited
    public boolean intersects (Point point)
    {
        return contains(point.getLocation());
    }

    @Override // documentation inherited
    public boolean intersects (Segment segment)
    {
        // see if we start inside the polygon
        Vector2f origin = segment.getStart();
        if (contains(origin)) {
            return true;
        }
        // check the segment against each edge (making sure it's on the right side)
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            if (a*origin.x + b*origin.y <= a*start.x + b*start.y &&
                    segment.intersects(start, end)) {
                return true;
            }
        }
        return false;
    }

    @Override // documentation inherited
    public boolean intersects (Circle circle)
    {
        // look for edges that the circle's center is outside
        Vector2f center = circle.getCenter();
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            float l2 = a*a + b*b;
            float d = a*center.x + b*center.y - a*start.x - b*start.y;
            if (d >= 0f) {
                continue;
            }
            // look at the next edge
            Vector2f next = _vertices[(ii + 2) % _vertices.length];
            a = end.y - next.y;
            b = next.x - end.x;
            if (a*center.x + b*center.y <= a*end.x + b*end.y) {
                // outside next edge; closest feature is end vertex
                return end.distanceSquared(center) <= circle.radius*circle.radius;
            }
            // check the previous edge
            Vector2f previous = _vertices[(ii + _vertices.length - 1) % _vertices.length];
            a = previous.y - start.y;
            b = start.x - previous.x;
            if (a*center.x + b*center.y >= a*previous.x + b*previous.y) {
                // inside previous edge; closest feature is edge
                return d*d <= l2*circle.radius*circle.radius;
            }
        }
        return true; // center is inside all edges
    }

    @Override // documentation inherited
    public boolean intersects (Capsule capsule)
    {
        // find the first edge that the origin is outside
        Vector2f origin = capsule.getStart(), terminus = capsule.getEnd();
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            float d = a*origin.x + b*origin.y - a*start.x - b*start.y;
            if (d >= 0f) {
                continue;
            }
            // check against the edge itself
            if (intersects(start, end, capsule.radius, origin, terminus)) {
                return true;
            }
            // now classify with respect to the adjacent edges
            Vector2f previous = _vertices[(ii + _vertices.length - 1) % _vertices.length];
            a = previous.y - start.y;
            b = start.x - previous.x;
            if (a*origin.x + b*origin.y <= a*previous.x + b*previous.y) {
                // left: check against previous edge
                return intersects(previous, start, capsule.radius, origin, terminus);
            }
            Vector2f next = _vertices[(ii + 2) % _vertices.length];
            a = end.y - next.y;
            b = next.x - end.x;
            if (a*origin.x + b*origin.y > a*end.x + b*end.y) {
                // middle: no dice
                return false;
            } else {
                // right: check against next edge
                return intersects(end, next, capsule.radius, origin, terminus);
            }
        }
        return true; // origin is inside all edges
    }

    @Override // documentation inherited
    public boolean intersects (Polygon polygon)
    {
        return intersectsOnAxes(polygon) && polygon.intersectsOnAxes(this);
    }

    @Override // documentation inherited
    public boolean intersects (Compound compound)
    {
        return compound.intersects(this);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Shape shape, Vector2f result)
    {
        return shape.getPenetration(this, result).negateLocal();
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Point point, Vector2f result)
    {
        return result.set(Vector2f.ZERO);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Segment segment, Vector2f result)
    {
        return result.set(Vector2f.ZERO);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Circle circle, Vector2f result)
    {
        // look for edges that the circle's center is outside
        Vector2f center = circle.getCenter();
        float mind = Float.MAX_VALUE;
        int midx = 0;
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            float l2 = a*a + b*b;
            float d = a*center.x + b*center.y - a*start.x - b*start.y;
            if (d >= 0f) {
                // keep track of the closest edge
                float nd = d / FloatMath.sqrt(l2);
                if (nd < mind) {
                    mind = nd;
                    midx = ii;
                }
                continue;
            }
            // look at the next edge
            Vector2f next = _vertices[(ii + 2) % _vertices.length];
            a = end.y - next.y;
            b = next.x - end.x;
            if (a*center.x + b*center.y <= a*end.x + b*end.y) {
                // outside next edge; closest feature is end vertex
                float dist = center.distance(end);
                if (dist > 0f) {
                    return center.subtract(end, result).multLocal(circle.radius / dist - 1f);
                }
            }
            // check the previous edge
            Vector2f previous = _vertices[(ii + _vertices.length - 1) % _vertices.length];
            a = previous.y - start.y;
            b = start.x - previous.x;
            if (a*center.x + b*center.y >= a*previous.x + b*previous.y) {
                // inside previous edge; closest feature is edge
                return result.set(end.y - start.y, start.x - end.x).multLocal(
                    circle.radius/FloatMath.sqrt(l2) + d/l2);
            }
        }

        // center is inside all edges, so push it out of the closest
        Vector2f start = _vertices[midx], end = _vertices[(midx + 1) % _vertices.length];
        return result.set(end.y - start.y, start.x - end.x).normalizeLocal().multLocal(
            circle.radius + mind);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Capsule capsule, Vector2f result)
    {
        return result.set(Vector2f.ZERO);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Polygon polygon, Vector2f result)
    {
        // Calculate the conves hull of the minkowski difference between the two polygons then
        // determine the shortest vector to the hull which will be the penetration vector
        Vector2f minDistance = getMinMinkowskyDifference(this, polygon, null);
        minDistance = getMinMinkowskyDifference(polygon, this, minDistance);
        return result.set(minDistance);
    }

    @Override // documentation inherited
    public Vector2f getPenetration (Compound compound, Vector2f result)
    {
        return compound.getPenetration(this, result).negateLocal();
    }

    @Override // documentation inherited
    public void draw (boolean outline)
    {
        GL11.glBegin(outline ? GL11.GL_LINE_LOOP : GL11.GL_POLYGON);
        for (Vector2f vertex : _vertices) {
            GL11.glVertex2f(vertex.x, vertex.y);
        }
        GL11.glEnd();
    }

    @Override // documentation inherited
    public ShapeConfig createConfig ()
    {
        ShapeConfig.Polygon polygon = new ShapeConfig.Polygon();
        polygon.vertices = new ShapeConfig.Vertex[_vertices.length];
        for (int ii = 0; ii < _vertices.length; ii++) {
            ShapeConfig.Vertex vertex = polygon.vertices[ii] = new ShapeConfig.Vertex();
            vertex.x = _vertices[ii].x;
            vertex.y = _vertices[ii].y;
        }
        return polygon;
    }

    @Override // documentation inherited
    public String toString ()
    {
        return "Poly:(" + StringUtil.join(_vertices) + ")";
    }

    /**
     * (Re)initializes the vertex array for the specified number of vertices.
     */
    protected void initVertices (int vcount)
    {
        _vertices = new Vector2f[vcount];
        for (int ii = 0; ii < vcount; ii++) {
            _vertices[ii] = new Vector2f();
        }
    }

    /**
     * Tests the edges of this polygon as potential separating axes for this polygon and the
     * specified other.
     *
     * @return false if the polygons are disjoint on any of this polygon's axes, true if they
     * intersect on all axes.
     */
    protected boolean intersectsOnAxes (Polygon other)
    {
        // consider each edge of this polygon as a potential separating axis
    OUTER:
        for (int ii = 0; ii < _vertices.length; ii++) {
            Vector2f start = _vertices[ii], end = _vertices[(ii + 1) % _vertices.length];
            float a = start.y - end.y;
            float b = end.x - start.x;
            float c = a*start.x + b*start.y;

            // if all vertices fall outside the edge, the polygons are disjoint
            for (Vector2f vertex : other._vertices) {
                if (a*vertex.x + b*vertex.y >= c) {
                    continue OUTER;
                }
            }
            return false;
        }
        return true;
    }

    /**
     * Calculates the minimum distance to the origin for the A polygon edges in the convex
     * hull of the Minkowski difference of the A and B polygons.
     */
    protected Vector2f getMinMinkowskyDifference (Polygon A, Polygon B, Vector2f minDistance)
    {
        boolean flip = minDistance != null;
        for (int ii = 0, nn = A.getVertexCount(); ii < nn; ii++) {
            Vector2f start = A.getVertex(ii);
            Vector2f end = A.getVertex((ii + 1) % nn);
            Vector2f sprime = Vector2f.ZERO;
            Vector2f eprime = Vector2f.ZERO;
            Vector2f perp = new Vector2f(start.y - end.y, end.x - start.x);
            float dot = Float.NEGATIVE_INFINITY;
            for (int jj = 0, mm = B.getVertexCount(); jj < mm; jj++) {
                float odot = perp.dot(B.getVertex(jj));
                if (odot > dot) {
                    dot = odot;
                    sprime = B.getVertex(jj);
                    eprime = sprime;
                } else if (odot == dot) {
                    eprime = B.getVertex(jj);
                }
            }
            if (flip) {
                sprime = sprime.subtract(start);
                eprime = eprime.subtract(end);
            } else {
                sprime = start.subtract(sprime);
                eprime = end.subtract(eprime);
            }
            Vector2f distance = new Vector2f();
            nearestPointOnSegment(sprime, eprime, Vector2f.ZERO, distance);
            if (minDistance == null || minDistance.distanceSquared(Vector2f.ZERO) >
                    distance.distanceSquared(Vector2f.ZERO)) {
                minDistance = distance;
            }
        }
        return minDistance;
    }

    /** The vertices of the polygon. */
    protected Vector2f[] _vertices;
}