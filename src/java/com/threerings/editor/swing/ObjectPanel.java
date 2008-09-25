//
// $Id$

package com.threerings.editor.swing;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import java.util.Arrays;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.util.ListUtil;

import com.threerings.util.DeepUtil;
import com.threerings.util.ReflectionUtil;

import com.threerings.editor.EditorMessageBundle;
import com.threerings.editor.Property;
import com.threerings.editor.util.EditorContext;

import static com.threerings.editor.Log.*;

/**
 * Allows editing an object of a known class.
 */
public class ObjectPanel extends BasePropertyEditor
    implements ActionListener, ChangeListener
{
    /**
     * Creates a new object panel.
     *
     * @param tlabel the translatable label to use for the type chooser.
     * @param types the selectable subtypes.
     * @param ancestors the ancestor properties from which constraints are inherited.
     * @param outer the outer object to use when instantiating inner classes.
     */
    public ObjectPanel (
        EditorContext ctx, String tlabel, Class[] types, Property[] ancestors, Object outer)
    {
        this(ctx, tlabel, types, ancestors, outer, false);
    }

    /**
     * Creates a new object panel.
     *
     * @param tlabel the translatable label to use for the type chooser.
     * @param types the selectable subtypes.
     * @param ancestors the ancestor properties from which constraints are inherited.
     * @param outer the outer object to use when instantiating inner classes.
     * @param omitColumns if true, do not add editors for the properties flagged as columns.
     */
    public ObjectPanel (
        EditorContext ctx, String tlabel, Class[] types,
        Property[] ancestors, Object outer, boolean omitColumns)
    {
        _ctx = ctx;
        _msgmgr = ctx.getMessageManager();
        _msgs = _msgmgr.getBundle(EditorMessageBundle.DEFAULT);
        _outer = outer;
        _types = types;

        setBackground(getDarkerBackground(ancestors.length));

        setLayout(new VGroupLayout(GroupLayout.NONE, GroupLayout.STRETCH, 5, GroupLayout.TOP));
        if (_types.length > 1) {
            JPanel tpanel = new JPanel();
            tpanel.setBackground(null);
            add(tpanel);
            tpanel.add(new JLabel(getLabel(tlabel) + ":"));
            String[] labels = new String[_types.length];
            for (int ii = 0; ii < _types.length; ii++) {
                labels[ii] = getLabel(_types[ii]);
            }
            tpanel.add(_box = new JComboBox(labels));
            _box.addActionListener(this);
            _values = new Object[_types.length];
        }
        add(_panel = new EditorPanel(
            _ctx, EditorPanel.CategoryMode.PANELS, ancestors, omitColumns));
        _panel.addChangeListener(this);
    }

    /**
     * Sets the value of the object being edited.
     */
    public void setValue (Object value)
    {
        if (_box != null) {
            // clear out the old entries
            Arrays.fill(_values, null);

            // put in the new entry
            int nidx = (value == null) ? 0 : ListUtil.indexOfRef(_types, value.getClass());
            _values[nidx] = value;
            _box.removeActionListener(this);
            _box.setSelectedIndex(nidx);
            _box.addActionListener(this);
        }
        if (_panel.getObject() == (_lvalue = value)) {
            _panel.update();
        } else {
            _panel.setObject(value);
        }
    }

    /**
     * Returns the current value of the object being edited.
     */
    public Object getValue ()
    {
        return _panel.getObject();
    }

    // documentation inherited from interface ActionListener
    public void actionPerformed (ActionEvent event)
    {
        // switch to a different type
        int idx = _box.getSelectedIndex();
        Object value = null;
        Class type = _types[idx];
        if (type != null) {
            value = _values[idx];
            if (value == null) {
                try {
                    _values[idx] = value = newInstance(type);
                } catch (Exception e) {
                    log.warning("Failed to create instance [type=" + type + "].", e);
                }
            }
            if (_lvalue != null && value != null) {
                // transfer state from shared ancestry
                DeepUtil.transfer(_lvalue, value);
            }
        }
        _panel.setObject(value);
        if (value != null) {
            _lvalue = value;
        }
        fireStateChanged();
    }

    // documentation inherited from interface ChangeListener
    public void stateChanged (ChangeEvent event)
    {
        fireStateChanged();
    }

    @Override // documentation inherited
    protected String getMousePath (Point pt)
    {
        return _panel.getMousePath();
    }

    /**
     * Creates a new instance of the specified type.
     */
    protected Object newInstance (Class type)
        throws Exception
    {
        // find the most specific constructor that can take the last value
        if (_lvalue != null) {
            boolean inner = ReflectionUtil.isInner(type);
            Class ltype = _lvalue.getClass();
            Constructor cctor = null;
            Class<?> cptype = null;
            for (Constructor ctor : type.getConstructors()) {
                Class<?>[] ptypes = ctor.getParameterTypes();
                if (inner ? (ptypes.length != 2 || !ptypes[0].isInstance(_outer)) :
                        (ptypes.length != 1)) {
                    continue;
                }
                Class<?> ptype = ptypes[ptypes.length - 1];
                if (ptype.isInstance(_lvalue) &&
                        (cctor == null || cptype.isAssignableFrom(ptype))) {
                    cctor = ctor;
                    cptype = ptype;
                }
            }
            if (cctor != null) {
                return inner ? cctor.newInstance(_outer, _lvalue) : cctor.newInstance(_lvalue);
            }
        }
        // fall back on default constructor
        return ReflectionUtil.newInstance(type, _outer);
    }

    /** Provides access to common services. */
    protected EditorContext _ctx;

    /** The type box. */
    protected JComboBox _box;

    /** The editor panel. */
    protected EditorPanel _panel;

    /** The outer object reference. */
    protected Object _outer;

    /** The list of available types. */
    protected Class[] _types;

    /** Stored values for each type. */
    protected Object[] _values;

    /** The last non-null value selected. */
    protected Object _lvalue;
}
