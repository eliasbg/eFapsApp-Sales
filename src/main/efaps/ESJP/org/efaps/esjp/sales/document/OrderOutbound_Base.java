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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.sales.Channel;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.util.EFapsException;
import org.efaps.util.cache.CacheReloadException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("bd08a90e-91ce-4f03-b1bc-921a53b71948")
@EFapsApplication("eFapsApp-Sales")
public abstract class OrderOutbound_Base
    extends AbstractDocumentSum
{

    /**
     * Executed from a Command execute event to create a new OrderOutbound.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
        connect2Derived(_parameter, createdDoc);
        connect2Object(_parameter, createdDoc);
        connect2Terms(_parameter, createdDoc);

        final File file = createReport(_parameter, createdDoc);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        executeProcess(_parameter, createdDoc);
        ret.put(ReturnValues.INSTANCE, createdDoc.getInstance());
        return ret;
    }

    /**
     * Executed from a Command execute event to create a new OrderOutbound.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final EditedDoc editedDoc = editDoc(_parameter);
        updatePositions(_parameter, editedDoc);
        updateConnection2Object(_parameter, editedDoc);

        final File file = createReport(_parameter, editedDoc);
        if (file != null) {
            ret.put(ReturnValues.VALUES, file);
            ret.put(ReturnValues.TRUE, true);
        }
        return ret;
    }



    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return connect2RecievingTicketTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selStatus = SelectBuilder.get().linkto(CISales.Document2DocumentAbstract.FromAbstractLink)
                        .attribute(CISales.OrderOutbound.Status);
        final SelectBuilder selOOInst = SelectBuilder.get().linkto(CISales.Document2DocumentAbstract.FromAbstractLink)
                        .instance();
        print.addSelect(selOOInst, selStatus);
        print.executeWithoutAccessCheck();

        final Instance ooInst = print.getSelect(selOOInst);
        final Status status = Status.get(print.<Long>getSelect(selStatus));
        final DocComparator comp = getComparator(_parameter, ooInst);
        final Map<Status, Status> maping = getStatusMapping4connect2RecievingTicker();
        if (comp.quantityIsZero() && maping.containsKey(status)) {
            final Update update = new Update(ooInst);
            update.add(CISales.OrderOutbound.Status, maping.get(status));
            update.executeWithoutAccessCheck();
        }
        return new Return();
    }

    /**
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return connect2IncomingInvoiceTrigger(final Parameter _parameter)
        throws EFapsException
    {
        final PrintQuery print = new PrintQuery(_parameter.getInstance());
        final SelectBuilder selStatus = SelectBuilder.get().linkto(CISales.Document2DocumentAbstract.FromAbstractLink)
                        .attribute(CISales.OrderOutbound.Status);
        final SelectBuilder selOOInst = SelectBuilder.get().linkto(CISales.Document2DocumentAbstract.FromAbstractLink)
                        .instance();
        print.addSelect(selOOInst, selStatus);
        print.executeWithoutAccessCheck();

        final Instance ooInst = print.getSelect(selOOInst);
        final Status status = Status.get(print.<Long>getSelect(selStatus));
        final DocComparator comp = getComparator(_parameter, ooInst);
        final Map<Status, Status> maping = getStatusMapping4connect2IncomingInvoice();
        if (comp.netIsZero() && maping.containsKey(status)) {
            final Update update = new Update(ooInst);
            update.add(CISales.OrderOutbound.Status, maping.get(status));
            update.executeWithoutAccessCheck();
        }
        return new Return();
    }

    @Override
    protected void add2UpdateMap4Contact(final Parameter _parameter,
                                         final Instance _contactInstance,
                                         final Map<String, Object> _map)
        throws EFapsException
    {
        super.add2UpdateMap4Contact(_parameter, _contactInstance, _map);
        if (Sales.ORDEROUTBOUNDACTIVATECONDITION.get()) {
            InterfaceUtils.appendScript4FieldUpdate(_map,
                            new Channel().getConditionJs(_parameter, _contactInstance,
                                            CISales.ChannelPurchaseCondition2Contact));
        }
    }

    @Override
    protected StringBuilder add2JavaScript4DocumentContact(final Parameter _parameter,
                                                           final List<Instance> _instances,
                                                           final Instance _contactInstance)
        throws EFapsException
    {
        final StringBuilder ret = super.add2JavaScript4DocumentContact(_parameter, _instances, _contactInstance);
        if (Sales.ORDEROUTBOUNDACTIVATECONDITION.get()) {
            ret.append(new Channel().getConditionJs(_parameter, _contactInstance,
                            CISales.ChannelPurchaseCondition2Contact));
        }
        return ret;
    }

    protected Map<Status, Status> getStatusMapping4connect2RecievingTicker()
        throws CacheReloadException
    {
        final Map<Status, Status> ret = new HashMap<>();
        ret.put(Status.find(CISales.OrderOutboundStatus.Open), Status.find(CISales.OrderOutboundStatus.Received));
        ret.put(Status.find(CISales.OrderOutboundStatus.Invoiced), Status.find(CISales.OrderOutboundStatus.Closed));
        return ret;
    }

    protected Map<Status, Status> getStatusMapping4connect2IncomingInvoice()
        throws CacheReloadException
    {
        final Map<Status, Status> ret = new HashMap<>();
        ret.put(Status.find(CISales.OrderOutboundStatus.Open), Status.find(CISales.OrderOutboundStatus.Invoiced));
        ret.put(Status.find(CISales.OrderOutboundStatus.Received), Status.find(CISales.OrderOutboundStatus.Closed));
        return ret;
    }

    protected DocComparator getComparator(final Parameter _parameter,
                                          final Instance _orderInst)
        throws EFapsException
    {
        final DocComparator ret = new DocComparator();
        ret.setDocInstance(_orderInst);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.OrderOutbound2IncomingInvoice);
        queryBldr.addType(CISales.OrderOutbound2RecievingTicket);
        queryBldr.addWhereAttrEqValue(CISales.Document2DocumentAbstract.FromAbstractLink, _orderInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selDocInst = SelectBuilder.get()
                        .linkto(CISales.Document2DocumentAbstract.ToAbstractLink)
                        .instance();
        multi.addSelect(selDocInst);
        multi.executeWithoutAccessCheck();
        while (multi.next()) {
            final Instance docInst = multi.getSelect(selDocInst);
            final DocComparator docComp = new DocComparator();
            docComp.setDocInstance(docInst);
            if (docInst.getType().isKindOf(CISales.IncomingInvoice.getType())) {
                ret.substractNet(docComp);
            } else {
                ret.substractQuantity(docComp);
            }
        }
        return ret;
    }

    @Override
    protected boolean isContact2JavaScript4Document(final Parameter _parameter,
                                                    final List<Instance> _instances)
        throws EFapsException
    {
        boolean ret = true;
        if (!_instances.isEmpty() && _instances.get(0).isValid()) {
            ret = !_instances.get(0).getType().isKindOf(CISales.ProductRequest.getType());
        }
        return ret;
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CISales.OrderOutbound;
    }
}
