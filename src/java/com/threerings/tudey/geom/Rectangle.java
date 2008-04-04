//
// $Id$

package com.threerings.tudey.geom;

import com.threerings.math.FloatMath;

/**
 * An axis-aligned rectangle.
 */
public final class Rectangle extends Shape
{
    /**
     * Creates a rectangle with the specified minimum and maximum extents.
     */
    public Rectangle (float minX, float minY, float maxX, float maxY)
    {
        set(minX, minY, maxX, maxY);
    }

    /**
     * Copy constructor.
     */
    public Rectangle (Rectangle other)
    {
        set(other);
    }

    /**
     * Creates an uninitialized rectangle.
     */
    public Rectangle ()
    {
    }

    /**
     * Copies the parameters of another rectangle.
     */
    public void set (Rectangle other)
    {
        set(other.getMinimumX(), other.getMinimumY(), other.getMaximumX(), other.getMaximumY());
    }

    /**
     * Sets the parameters of the rectangle.
     */
    public void set (float minX, float minY, float maxX, float maxY)
    {
        if (_minX == minX && _minY == minY && _maxX == maxX && _maxY == maxY) {
            return;
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Maximum extents must be greater than the minimums.");
        }
        willMove();
        _minX = minX;
        _minY = minY;
        _maxX = maxX;
        _maxY = maxY;
        updateBounds();
        didMove();
    }

    /**
     * Returns the minimum x extent of the rectangle.
     */
    public float getMinimumX ()
    {
        return _minX;
    }

    /**
     * Returns the minimum y extent of the rectangle.
     */
    public float getMinimumY ()
    {
        return _minY;
    }

    /**
     * Returns the maximum x extent of the rectangle.
     */
    public float getMaximumX ()
    {
        return _maxX;
    }

    /**
     * Returns the maximum y extent of the rectangle.
     */
    public float getMaximumY ()
    {
        return _maxY;
    }

    @Override // documentation inherited
    public void updateBounds ()
    {
        _bounds.set(_minX, _minY, _maxX, _maxY);
    }

    @Override // documentation inherited
    public boolean intersects (Shape other)
    {
        return other.checkIntersects(this);
    }

    @Override // documentation inherited
    public boolean equals (Object other)
    {
        Rectangle orect;
        return super.equals(other) &&
            _minX == (orect = (Rectangle)other)._minX && _minY == orect._minY &&
            _maxX == orect._maxX && _maxY == orect._maxY;
    }

    @Override // documentation inherited
    public String toString ()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("Rectangle[");
        builder.append("minx=").append(_minX).append(", ");
        builder.append("miny=").append(_minY).append(", ");
        builder.append("maxx=").append(_maxX).append(", ");
        builder.append("maxy=").append(_maxY);
        builder.append("]");
        return builder.toString();
    }

    @Override // documentation inherited
    protected boolean checkIntersects (Point point)
    {
        return checkIntersects(point.getX(), point.getY());
    }

    @Override // documentation inherited
    protected boolean checkIntersects (Line line)
    {
        // check if the segment starts or ends inside the rectangle
        if (checkIntersects(line.getX1(), line.getY1()) ||
                checkIntersects(line.getX2(), line.getY2())) {
            return true;
        }
        // check if the segment intersects the rectangle
        if (line.getX2() > line.getX1()) {
            float t = (_minX - line.getX1()) / (line.getX2() - line.getX1());
            if (checkIntersectsX(line, t)) {
                return true;
            }
        } else if (line.getX1() > line.getX2()) {
            float t = (_maxX - line.getX1()) / (line.getX2() - line.getX1());
            if (checkIntersectsX(line, t)) {
                return true;
            }
        }
        if (line.getY2() > line.getY1()) {
            float t = (_minY - line.getY1()) / (line.getY2() - line.getY1());
            if (checkIntersectsY(line, t)) {
                return true;
            }
        } else if (line.getY1() > line.getY2()) {
            float t = (_maxY - line.getY1()) / (line.getY2() - line.getY1());
            if (checkIntersectsY(line, t)) {
                return true;
            }
        }
        return false;
    }

    @Override // documentation inherited
    protected boolean checkIntersects (Circle circle)
    {
        // the center of the circle is in one of nine sections:
        // [lower/middle/upper][left/middle/right]
        float dx = 0f, dy = 0f;
        if (circle.getX() < _minX) { // left
            dx = _minX - circle.getX();
            if (circle.getY() < _minY) { // lower
                dy = _minY - circle.getY();
            } else if (circle.getY() > _maxY) { // upper
                dy = circle.getY() - _maxY;
            } else { // middle
                return dx <= circle.getRadius();
            }
        } else if (circle.getX() > _maxX) { // right
            dx = circle.getX() - _maxX;
            if (circle.getY() < _minY) { // lower
                dy = _minY - circle.getY();
            } else if (circle.getY() > _maxY) { // upper
                dy = circle.getY() - _maxY;
            } else { // middle
                return dx <= circle.getRadius();
            }
        } else { // middle
            if (circle.getY() < _minY) { // lower
                return (_minY - circle.getY()) <= circle.getRadius();
            } else if (circle.getY() > _maxY) { // upper
                return (circle.getY() - _maxY) <= circle.getRadius();
            } else { // middle (contained)
                return true;
            }
        }
        return (dx*dx + dy*dy) <= circle.getRadius()*circle.getRadius();
    }

    @Override // documentation inherited
    protected boolean checkIntersects (Rectangle rectangle)
    {
        return _minX <= rectangle.getMaximumX() && _minY <= rectangle.getMaximumY() &&
            _maxX >= rectangle.getMinimumX() && _maxY >= rectangle.getMinimumY();
    }

    @Override // documentation inherited
    protected boolean checkIntersects (Capsule capsule)
    {
        return capsule.checkIntersects(this);
    }

    /**
     * Helper method for {@link #checkIntersects}.
     */
    protected boolean checkIntersects (float x, float y)
    {
        return x >= _minX && x <= _maxX && y >= _minY && y <= _maxY;
    }

    /**
     * Helper method for {@link #checkIntersects(Line)}.
     */
    protected boolean checkIntersectsX (Line line, float t)
    {
        if (t < 0f || t > 1f) {
            return false;
        }
        float y = line.getY1() + t * (line.getY2() - line.getY1());
        return (y >= _minX && y <= _maxY);
    }

    /**
     * Helper method for {@link #checkIntersects(Line)}.
     */
    protected boolean checkIntersectsY (Line line, float t)
    {
        if (t < 0f || t > 1f) {
            return false;
        }
        float x = line.getX1() + t * (line.getX2() - line.getX1());
        return (x >= _minX && x <= _maxX);
    }

    /** The minimum extent of the rectangle. */
    protected float _minX, _minY;

    /** The maximum extent of the rectangle. */
    protected float _maxX, _maxY;
}
