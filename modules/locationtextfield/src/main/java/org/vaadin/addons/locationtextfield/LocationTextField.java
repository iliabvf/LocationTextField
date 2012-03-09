/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.vaadin.addons.locationtextfield;

import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.FieldEvents;
import com.vaadin.terminal.PaintException;
import com.vaadin.terminal.PaintTarget;
import com.vaadin.ui.AbstractTextField;
import com.vaadin.ui.ClientWidget;
import com.vaadin.ui.Select;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.vaadin.addons.locationtextfield.client.ui.VLocationTextField;

@ClientWidget(value = VLocationTextField.class, loadStyle = ClientWidget.LoadStyle.EAGER)
public class LocationTextField<T extends GeocodedLocation> extends Select {

    private final transient Map<String, GeocodedLocation> locations = new WeakHashMap<String, GeocodedLocation>();
    private final LocationProvider<T> locationProvider;

    /**
     * The text content when the last messages to the server was sent.
     */
    private String lastKnownTextContent;

    /**
     * Flag indicating that a text change event is pending to be triggered.
     * Cleared by {@link #setInternalValue(Object)} and when the event is fired.
     */
    private boolean textChangeEventPending;

    private AbstractTextField.TextChangeEventMode textChangeEventMode = AbstractTextField.TextChangeEventMode.LAZY;

    private final int DEFAULT_TEXTCHANGE_TIMEOUT = 1000;

    private int textChangeEventTimeout = DEFAULT_TEXTCHANGE_TIMEOUT;

    /**
     * Track whether the value on the server has actually changed to avoid
     * updating the text in the input element on every repaint
     */
    private boolean localValueChanged = true;

    private boolean autoSelectOnSingleResult;
    private boolean selecting;
    private boolean enterKeyFiresTextChange;

    public LocationTextField(LocationProvider<T> locationProvider, Class<T> clazz) {
        this(locationProvider, clazz, null);
    }

    @SuppressWarnings("deprecation")
    public LocationTextField(final LocationProvider<T> locationProvider, Class<T> clazz, String caption) {
        super(caption, new BeanItemContainer<T>(clazz));
        this.locationProvider = locationProvider;
        super.setMultiSelect(false);
        super.setFilteringMode(FILTERINGMODE_OFF);
        super.setImmediate(true);
        super.setNewItemsAllowed(false);
        super.setReadOnly(false);
        super.setNullSelectionAllowed(false);
        this.setItemCaptionPropertyId("geocodedAddress");
    }

    @Override
    public Class<?> getType() {
        return GeocodedLocation.class;
    }

    @Override
    public void setFilteringMode(int filteringMode) {
        // nothing
    }

    @Override
    public void setImmediate(boolean immediate) {
        // nothing
    }

    @Override
    public void setNewItemsAllowed(boolean newItemsAllowed) {
        // nothing
    }

    @Override
    public void setNullSelectionAllowed(boolean nullSelectionAllowed) {
        // nothing
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        // nothing
    }

    @Override
    @Deprecated
    public void setMultiSelect(boolean multiSelect) {
        // nothing
    }

    /**
     * Whether or not pressing the ENTER key inside the text box fires a text change event
     */
    public boolean isEnterKeyFiresTextChange() {
        return this.enterKeyFiresTextChange;
    }
    public void setEnterKeyFiresTextChange(boolean enterKeyFiresTextChange) {
        this.enterKeyFiresTextChange = enterKeyFiresTextChange;
    }

    /**
     * Paints the content of this component.
     *
     * @param target
     *            the Paint Event.
     * @throws com.vaadin.terminal.PaintException
     *             if the paint operation failed.
     */
    @Override
    public void paintContent(PaintTarget target) throws PaintException {
        super.paintContent(target);

        if (localValueChanged) {
            target.addAttribute(VLocationTextField.ATTR_TEXT_CHANGED, true);
            localValueChanged = false;
        }

        target.addVariable(this, "filter", this.lastKnownTextContent);

        target.addAttribute(VLocationTextField.ATTR_TEXTCHANGE_EVENTMODE, getTextChangeEventMode().toString());
        target.addAttribute(VLocationTextField.ATTR_TEXTCHANGE_TIMEOUT, getTextChangeTimeout());
        target.addAttribute(VLocationTextField.ATTR_ENTER_KEY_FIRES_TEXT_CHANGE, this.isEnterKeyFiresTextChange());
    }

    @Override
    protected void setValue(Object newValue, boolean repaintIsNotNeeded) throws ReadOnlyException, ConversionException {
        if (notEqual(newValue, super.getValue())) {
            // The client should use the new value
            localValueChanged = true;
            if (!repaintIsNotNeeded) {
                // Repaint even if super.setValue doesn't detect any change
                requestRepaint();
            }
        }
        super.setValue(newValue, repaintIsNotNeeded);
    }

    private static boolean notEqual(Object newValue, Object oldValue) {
        return oldValue != newValue && (newValue == null || !newValue.equals(oldValue));
    }

    @SuppressWarnings("unchecked")
    public BeanItemContainer<T> getContainerDataSource() {
        return (BeanItemContainer<T>)super.getContainerDataSource();
    }

    /**
     * Convenience method for explicitly setting the location
     * @param location
     */
    @SuppressWarnings("unchecked")
    public void setLocation(T location) {
        getContainerDataSource().removeAllItems();
        if (location != null) {
            getContainerDataSource().addBean(location);
            this.lastKnownTextContent = location.getGeocodedAddress();
        } else {
            this.lastKnownTextContent = null;
        }
        super.setValue(location);
    }

    /**
     * Removes all options and resets text field
     */
    public void clear() {
        this.setLocation(null);
    }

    @Override
    public void changeVariables(Object source, Map<String, Object> variables) {
        super.changeVariables(source, variables);

        // Sets the text
        if (variables.containsKey("filter")) {

            // Only do the setting if the string representation of the value
            // has been updated
            String newValue = ("" + variables.get("filter")).trim();

            if (!newValue.equals(lastKnownTextContent) && !"".equals(newValue)) {
                GeocodedLocation newLocation;
                synchronized (this.locations) {
                    newLocation = this.locations.get(newValue); // this is the geocoded address if a match is found
                }
                if (newLocation != null) {
                    this.select(newLocation);
                } else {
                    lastKnownTextContent = newValue;
                    textChangeEventPending = true;
                }
            }
        }
        firePendingTextChangeEvent();

        if (variables.containsKey(FieldEvents.FocusEvent.EVENT_ID)) {
            fireEvent(new FieldEvents.FocusEvent(this));
        }
        if (variables.containsKey(FieldEvents.BlurEvent.EVENT_ID)) {
            fireEvent(new FieldEvents.BlurEvent(this));
        }
    }

    @Override
    public void select(Object itemId) {
        this.selecting = true;
        try {
            super.select(itemId);
        } finally {
            this.selecting = false;
        }
    }

    private void firePendingTextChangeEvent() {
        if (textChangeEventPending) {
            textChangeEventPending = false;
            update();
        }
    }

    /**
     * Allows developer to set a known address string to be geocoded on the server-side
     * @param address String representation of an address
     */
    public void geocode(String address) {
        this.lastKnownTextContent = address;
        this.textChangeEventPending = true;
        firePendingTextChangeEvent();
    }

    /**
     * Whether or not to auto-select a location when there is only one result
     */
    public boolean isAutoSelectOnSingleResult() {
        return autoSelectOnSingleResult;
    }
    public void setAutoSelectOnSingleResult(boolean autoSelectOnSingleResult) {
        this.autoSelectOnSingleResult = autoSelectOnSingleResult;
    }

    private void update() {
        try {
            String addr = this.lastKnownTextContent;
            Collection<T> locs;
            if (addr != null && !"".equals(addr.trim()))
                locs = this.locationProvider.geocode(addr.trim());
            else
                locs = Collections.emptyList();
            getContainerDataSource().removeAllItems();
            synchronized (this.locations) {
                this.locations.clear();
                for (T loc : locs) {
                    this.locations.put(loc.getGeocodedAddress(), loc);
                    getContainerDataSource().addBean(loc);
                }
                if (this.locations.size() == 1 && isAutoSelectOnSingleResult())
                    this.select(this.locations.values().iterator().next());
            }
            requestRepaint();
        } catch (GeocodingException e) {
            e.printStackTrace();
            // ignore or log
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getValue() {
        Object value = super.getValue();
        if (value instanceof String) {
            this.setValue(value);
            return this.getValue();
        } if (value instanceof GeocodedLocation)
            return (T)value;
        return null;
    }

    @Override
    public void setValue(Object value) {
        if (this.selecting) {
            super.setValue(value);
            return;
        }

        Object newValue = null;
        if (value == null) {
            this.clear();
            return;
        } else if (value instanceof String) {
            geocode((String)value);
            final List<Object> itemIds = new ArrayList<Object>(getItemIds());
            final int index = (isNullSelectionAllowed() ? 1 : 0);
            if (itemIds.size() > (isNullSelectionAllowed() ? 1 : 0))
                newValue = itemIds.get(index);
        } else if (value instanceof GeocodedLocation) {
            newValue = value;
            this.lastKnownTextContent = ((GeocodedLocation)value).getGeocodedAddress();
        }
        super.setValue(newValue);
    }

    /**
     * Sets the mode how the TextField triggers {@link com.vaadin.event.FieldEvents.TextChangeEvent}s.
     *
     * @param inputEventMode
     *            the new mode
     *
     * @see AbstractTextField.TextChangeEventMode
     */
    public void setTextChangeEventMode(AbstractTextField.TextChangeEventMode inputEventMode) {
        if (inputEventMode == AbstractTextField.TextChangeEventMode.EAGER)
            inputEventMode = AbstractTextField.TextChangeEventMode.LAZY;
        textChangeEventMode = inputEventMode;
        requestRepaint();
    }

    /**
     * @return the mode used to trigger {@link com.vaadin.event.FieldEvents.TextChangeEvent}s.
     */
    public AbstractTextField.TextChangeEventMode getTextChangeEventMode() {
        return textChangeEventMode;
    }

    /**
     * The text change timeout modifies how often text change events are
     * communicated to the application when {@link #getTextChangeEventMode()} is
     * {@link AbstractTextField.TextChangeEventMode#LAZY} or {@link AbstractTextField.TextChangeEventMode#TIMEOUT}.
     *
     *
     * @see #getTextChangeEventMode()
     *
     * @param timeout
     *            the timeout in milliseconds
     */
    public void setTextChangeTimeout(int timeout) {
        textChangeEventTimeout = timeout;
        requestRepaint();
    }

    /**
     * Gets the timeout used to fire {@link com.vaadin.event.FieldEvents.TextChangeEvent}s when the
     * {@link #getTextChangeEventMode()} is {@link AbstractTextField.TextChangeEventMode#LAZY} or
     * {@link AbstractTextField.TextChangeEventMode#TIMEOUT}.
     *
     * @return the timeout value in milliseconds
     */
    public int getTextChangeTimeout() {
        return textChangeEventTimeout;
    }
}
