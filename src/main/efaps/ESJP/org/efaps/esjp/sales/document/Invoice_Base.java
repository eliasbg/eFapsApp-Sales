/*
 * Copyright 2003 - 2015 The eFaps Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.efaps.esjp.sales.document;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.QueryBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.sales.Channel;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("43417000-af54-4cb5-a266-4e6df2ed793e")
@EFapsApplication("eFapsApp-Sales")
public abstract class Invoice_Base
    extends AbstractDocumentSum
{

    /**
     * Method for create a new Quotation.
     *
     * @param _parameter Parameter as passed from eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
        connect2Derived(_parameter, createdDoc);
        connect2Object(_parameter, createdDoc);

        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.ISPERCEPTIONAGENT)) {
            new PerceptionCertificate().create4Doc(_parameter, createdDoc);
        }

        final File file = createReport(_parameter, createdDoc);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    /**
     * Method for create a new Quotation.
     *
     * @param _parameter Parameter as passed from eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final EditedDoc editedDoc = editDoc(_parameter);
        updatePositions(_parameter, editedDoc);

        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.INVOICEACTIVATECONDITION)) {
            final Instance condInst = Instance
                            .get(_parameter.getParameterValue(CIFormSales.Sales_InvoiceForm.condition4edit.name));
            if (condInst.isValid()) {
                final QueryBuilder queryBldr = new QueryBuilder(CISales.ChannelSalesCondition2Invoice);
                queryBldr.addWhereAttrEqValue(CISales.ChannelSalesCondition2Invoice.ToLink, _parameter.getInstance());
                final List<Instance> relInsts = queryBldr.getQuery().execute();
                Update update;
                if (relInsts.isEmpty()) {
                    update = new Insert(CISales.ChannelSalesCondition2Invoice);
                    update.add(CISales.ChannelSalesCondition2Invoice.ToLink, _parameter.getInstance());
                } else {
                    update = new Update(relInsts.get(0));
                }
                update.add(CISales.ChannelSalesCondition2Invoice.FromLink, condInst);
                update.execute();
            }
        }
        final File file = createReport(_parameter, editedDoc);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CISales.Invoice;
    }

    @Override
    protected void add2UpdateMap4Contact(final Parameter _parameter,
                                         final Instance _contactInstance,
                                         final Map<String, Object> _map)
        throws EFapsException
    {
        super.add2UpdateMap4Contact(_parameter, _contactInstance, _map);
        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.INVOICEACTIVATECONDITION)) {
            InterfaceUtils.appendScript4FieldUpdate(_map, new Channel().getConditionJs(_parameter, _contactInstance,
                            CISales.ChannelSalesCondition2Contact));
        }
        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.INVOICEFROMDELIVERYNOTE)) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.DeliveryNote);
            queryBldr.addWhereAttrEqValue(CISales.DeliveryNote.Status, Status.find(CISales.DeliveryNoteStatus.Open));
            InterfaceUtils.appendScript4FieldUpdate(_map, getJS4Doc4Contact(_parameter, _contactInstance,
                            CIFormSales.Sales_InvoiceForm.deliveryNotes.name, queryBldr));
        }
    }

    @Override
    protected StringBuilder add2JavaScript4DocumentContact(final Parameter _parameter,
                                                           final List<Instance> _instances,
                                                           final Instance _contactInstance)
        throws EFapsException
    {
        final StringBuilder ret = super.add2JavaScript4DocumentContact(_parameter, _instances, _contactInstance);
        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.INVOICEACTIVATECONDITION)) {
            ret.append(new Channel().getConditionJs(_parameter, _contactInstance,
                            CISales.ChannelSalesCondition2Contact));
        }
        if (Sales.getSysConfig().getAttributeValueAsBoolean(SalesSettings.INVOICEFROMDELIVERYNOTE)) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.DeliveryNote);
            queryBldr.addWhereAttrEqValue(CISales.DeliveryNote.Status, Status.find(CISales.DeliveryNoteStatus.Open));
            ret.append(getJS4Doc4Contact(_parameter, _contactInstance,
                            CIFormSales.Sales_InvoiceForm.deliveryNotes.name, queryBldr));
        }
        return ret;
    }
}
