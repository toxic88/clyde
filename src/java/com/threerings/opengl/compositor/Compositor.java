//
// $Id$

package com.threerings.opengl.compositor;

import java.util.ArrayList;
import java.util.HashMap;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Maps;

import com.samskivert.util.ComparableArrayList;

import com.threerings.opengl.camera.Camera;
import com.threerings.opengl.compositor.config.RenderQueueConfig;
import com.threerings.opengl.compositor.config.RenderSchemeConfig;
import com.threerings.opengl.renderer.Color4f;
import com.threerings.opengl.renderer.Renderer;
import com.threerings.opengl.renderer.state.ColorMaskState;
import com.threerings.opengl.renderer.state.DepthState;
import com.threerings.opengl.renderer.state.StencilState;
import com.threerings.opengl.util.GlContext;
import com.threerings.opengl.util.Renderable;

/**
 * Handles the process of compositing the scene from its various elements.
 */
public class Compositor
{
    /**
     * Creates a new compositor.
     */
    public Compositor (GlContext ctx)
    {
        _ctx = ctx;
        _group = new RenderQueue.Group(ctx);
    }

    /**
     * Initializes the compositor once the renderer has been initialized.
     */
    public void init ()
    {
        _camera.getViewport().set(_ctx.getRenderer().getViewport());
    }

    /**
     * Returns a reference to the camera.
     */
    public Camera getCamera ()
    {
        return _camera;
    }

    /**
     * Returns a reference to the background color.
     */
    public Color4f getBackgroundColor ()
    {
        return _backgroundColor;
    }

    /**
     * Adds an element to the list of scene roots.
     */
    public void addRoot (Renderable root)
    {
        _roots.add(root);
    }

    /**
     * Removes an element from the list of scene roots.
     */
    public void removeRoot (Renderable root)
    {
        _roots.remove(root);
    }

    /**
     * Renders the composited scene.
     */
    public void renderScene ()
    {
        // start by requesting that the roots enqueue themselves and register their dependencies
        for (int ii = 0, nn = _roots.size(); ii < nn; ii++) {
            _roots.get(ii).enqueue();
        }
        // store and reset the color clear setting
        boolean skipColorClear = _skipColorClear;
        _skipColorClear = false;

        // reset the renderer stats
        Renderer renderer = _ctx.getRenderer();
        renderer.resetStats();

        // resolve and clear the set of dependencies
        for (Dependency dependency : _dependencies.values()) {
            dependency.resolve();
        }
        _dependencies.clear();

        // sort the queues in preparation for rendering
        _group.sortQueues();

        // clear the depth and stencil buffers (and the color buffer, if necessary)
        int bits = GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT;
        if (!skipColorClear) {
            bits |= GL11.GL_COLOR_BUFFER_BIT;
            renderer.setClearColor(_backgroundColor);
            renderer.setState(ColorMaskState.ALL);
        }
        renderer.setClearDepth(1f);
        renderer.setState(DepthState.TEST_WRITE);
        renderer.setClearStencil(0);
        renderer.setState(StencilState.DISABLED);
        GL11.glClear(bits);

        // apply the camera state, render and clear the queues
        _camera.apply(renderer);
        _group.renderQueues();
        _group.clearQueues();

        // allow the renderer to clean up
        renderer.cleanup();
    }

    /**
     * Adds an element to the list of render dependencies.
     */
    public void addDependency (Dependency dependency)
    {
        // if we displace another dependency, merge it in
        Dependency previous = _dependencies.put(dependency, dependency);
        if (previous != null) {
            dependency.merge(previous);
        }
    }

    /**
     * Sets the flag indicating that we need not clear the color buffer before rendering the
     * frame.
     */
    public void setSkipColorClear ()
    {
        _skipColorClear = true;
    }

    /**
     * Retrieves a reference to a render queue.
     */
    public RenderQueue getQueue (String name)
    {
        return _group.getQueue(name);
    }

    /**
     * Resets the list of render queues.
     */
    public void resetQueues ()
    {
        _group = new RenderQueue.Group(_ctx);
    }

    /**
     * Returns a reference to the base render queue group.
     */
    public RenderQueue.Group getGroup ()
    {
        return _group;
    }

    /** The application context. */
    protected GlContext _ctx;

    /** The camera. */
    protected Camera _camera = new Camera();

    /** The background color. */
    protected Color4f _backgroundColor = new Color4f(0f, 0f, 0f, 0f);

    /** The roots of the view. */
    protected ArrayList<Renderable> _roots = new ArrayList<Renderable>();

    /** The current set of dependencies. */
    protected HashMap<Dependency, Dependency> _dependencies = Maps.newHashMap();

    /** When set, indicates that we need not clear the color buffer. */
    protected boolean _skipColorClear;

    /** The base render queue group. */
    protected RenderQueue.Group _group;
}
