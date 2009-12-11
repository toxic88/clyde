//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2009 Three Rings Design, Inc.
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

package com.threerings.opengl.compositor;

import java.lang.ref.SoftReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import com.samskivert.util.QuickSort;

import com.threerings.opengl.camera.Camera;
import com.threerings.opengl.compositor.config.RenderEffectConfig;
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
 * Handles the process of compositing the view from its various elements.
 */
public class Compositor
{
    /**
     * Represents the saved state of the compositor.
     */
    public static class State
    {
        /**
         * Swaps this state with that of the specified compositor.
         */
        public void swap (Compositor compositor)
        {
            // swap the camera
            Camera ocamera = _camera;
            _camera = compositor._camera;
            compositor._camera = ocamera;

            // the dependencies
            Map<Dependency, Dependency> odeps = _dependencies;
            _dependencies = compositor._dependencies;
            compositor._dependencies = odeps;

            // the effects
            List<RenderEffect> oeffects = _combinedEffects;
            _combinedEffects = compositor._combinedEffects;
            compositor._combinedEffects = oeffects;

            // the color clear skip state
            boolean oskip = _skipColorClear;
            _skipColorClear = compositor._skipColorClear;
            compositor._skipColorClear = oskip;

            // and the group state
            _groupState.swap(compositor._group);
        }

        /** The stored camera. */
        protected Camera _camera = new Camera();

        /** The stored dependencies. */
        protected Map<Dependency, Dependency> _dependencies = Maps.newHashMap();

        /** The stored combined effects. */
        protected List<RenderEffect> _combinedEffects = Lists.newArrayList();

        /** The stored color clear skip state. */
        protected boolean _skipColorClear;

        /** The stored render queue group state. */
        protected RenderQueue.Group.State _groupState = new RenderQueue.Group.State();
    }

    /**
     * Creates a new compositor.
     */
    public Compositor (GlContext ctx)
    {
        _ctx = ctx;
        _group = new RenderQueue.Group(ctx);
    }

    /**
     * Sets the camera reference.
     */
    public void setCamera (Camera camera)
    {
        _camera = camera;
    }

    /**
     * Returns a reference to the camera.
     */
    public Camera getCamera ()
    {
        return _camera;
    }

    /**
     * Returns a reference to the default background color.
     */
    public Color4f getDefaultBackgroundColor ()
    {
        return _defaultBackgroundColor;
    }

    /**
     * Sets the background color reference.
     *
     * @param color the background color, or null to use the default.
     */
    public void setBackgroundColor (Color4f color)
    {
        _backgroundColor = color;
    }

    /**
     * Returns the background color reference.
     */
    public Color4f getBackgroundColor ()
    {
        return _backgroundColor;
    }

    /**
     * Adds an element to the list of view roots.
     */
    public void addRoot (Renderable root)
    {
        _roots.add(root);
    }

    /**
     * Removes an element from the list of view roots.
     */
    public void removeRoot (Renderable root)
    {
        _roots.remove(root);
    }

    /**
     * Adds an effect to apply.
     */
    public void addEffect (RenderEffect effect)
    {
        _effects.add(effect);
    }

    /**
     * Removes an effect.
     */
    public void removeEffect (RenderEffect effect)
    {
        _effects.remove(effect);
    }

    /**
     * Renders the composited view.
     */
    public void renderView ()
    {
        // start by requesting that the roots enqueue themselves and register their dependencies
        for (int ii = 0, nn = _roots.size(); ii < nn; ii++) {
            _roots.get(ii).enqueue();
        }
        // reset the renderer stats
        Renderer renderer = _ctx.getRenderer();
        renderer.resetStats();

        // add the in-built effects
        _combinedEffects.addAll(_effects);

        // resolve the set of dependencies
        for (Dependency dependency : _dependencies.values()) {
            dependency.resolve();
        }

        // sort the queues in preparation for rendering
        _group.sortQueues();

        // apply the camera state
        _camera.apply(renderer);

        // process the effects in reverse order
        QuickSort.sort(_combinedEffects);
        renderPrevious(_combinedEffects.size());

        // clean up
        for (Dependency dependency : _dependencies.values()) {
            dependency.cleanup();
        }
        _dependencies.clear();
        _skipColorClear = false;
        _group.clearQueues();
        _combinedEffects.clear();
        renderer.cleanup();
    }

    /**
     * Starts a subrender operation.
     *
     * @return the stored compositor state.
     */
    public State startSubrender ()
    {
        _subrenderDepth++;

        // get a state from the pool and swap it for the compositor state
        State state = getStateFromPool();
        state.swap(this);
        return state;
    }

    /**
     * Finishes a subrender operation.
     *
     * @param ostate the state to restore.
     */
    public void endSubrender (State ostate)
    {
        _subrenderDepth--;

        // swap out the state and return it to the pool
        ostate.swap(this);
        _statePool.add(new SoftReference<State>(ostate));
    }

    /**
     * Returns the currently number of subrender levels.
     */
    public int getSubrenderDepth ()
    {
        return _subrenderDepth;
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
     * Adds an effect associated with a dependency.
     */
    public void addDependencyEffect (RenderEffectConfig config)
    {
        SoftReference<RenderEffect> ref = _cachedEffects.get(config);
        RenderEffect effect = (ref == null) ? null : ref.get();
        if (effect == null) {
            _cachedEffects.put(config, new SoftReference<RenderEffect>(
                effect = new RenderEffect(_ctx, _ctx.getScope(), config)));
        }
        _combinedEffects.add(effect);
    }

    /**
     * For the specified index within the list of combined effects, renders the previous
     * contents.
     */
    public void renderPrevious (int idx)
    {
        int minPriority = Integer.MIN_VALUE, maxPriority = Integer.MAX_VALUE;
        if (idx > 0) {
            int pidx = idx - 1;
            RenderEffect peffect = _combinedEffects.get(pidx);
            peffect.render(pidx);
            minPriority = peffect.getPriority() + 1;
        }
        if (idx < _combinedEffects.size()) {
            maxPriority = _combinedEffects.get(idx).getPriority();
        }
        renderQueues(minPriority, maxPriority);
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

    /**
     * Renders the contents of the queues within the specified priority range.
     */
    protected void renderQueues (int minPriority, int maxPriority)
    {
        // if the range includes the lower bound, perform the clear
        if (minPriority == Integer.MIN_VALUE) {
            Renderer renderer = _ctx.getRenderer();
            int bits = GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT;
            if (!_skipColorClear) {
                bits |= GL11.GL_COLOR_BUFFER_BIT;
                renderer.setClearColor(
                    _backgroundColor == null ? _defaultBackgroundColor : _backgroundColor);
                renderer.setState(ColorMaskState.ALL);
            }
            renderer.setClearDepth(1f);
            renderer.setState(DepthState.TEST_WRITE);
            renderer.setClearStencil(0);
            renderer.setState(StencilState.DISABLED);
            GL11.glClear(bits);
        }
        _group.renderQueues(RenderQueue.NORMAL_TYPE, minPriority, maxPriority);
    }

    /**
     * Retrieves a state object from the shared pool.
     */
    protected State getStateFromPool ()
    {
        for (int ii = _statePool.size() - 1; ii >= 0; ii--) {
            State state = _statePool.remove(ii).get();
            if (state != null) {
                return state;
            }
        }
        return new State();
    }

    /** The application context. */
    protected GlContext _ctx;

    /** The camera. */
    protected Camera _camera = new Camera();

    /** The default background color. */
    protected Color4f _defaultBackgroundColor = new Color4f(0f, 0f, 0f, 0f);

    /** The current background color, or <code>null</code> to use the default. */
    protected Color4f _backgroundColor;

    /** The roots of the view. */
    protected List<Renderable> _roots = Lists.newArrayList();

    /** The non-dependency render effects. */
    protected List<RenderEffect> _effects = Lists.newArrayList();

    /** The current set of dependencies. */
    protected Map<Dependency, Dependency> _dependencies = Maps.newHashMap();

    /** The combined list of render effects. */
    protected List<RenderEffect> _combinedEffects = Lists.newArrayList();

    /** When set, indicates that we need not clear the color buffer. */
    protected boolean _skipColorClear;

    /** The base render queue group. */
    protected RenderQueue.Group _group;

    /** The current subrender depth. */
    protected int _subrenderDepth;

    /** Cached render effects. */
    protected Map<RenderEffectConfig, SoftReference<RenderEffect>> _cachedEffects =
        Maps.newIdentityHashMap();

    /** A pool of state objects to reuse. */
    protected List<SoftReference<State>> _statePool = Lists.newArrayList();
}
