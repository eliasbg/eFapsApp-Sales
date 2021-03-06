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
import java.math.BigDecimal;
import java.util.List;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.ci.CIType;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.StandartReport_Base.JasperActivation;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.sales.payment.DocumentUpdate;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("a66a61fd-487a-4764-9e72-f65050c1d39e")
@EFapsApplication("eFapsApp-Sales")
public abstract class CreditNote_Base
    extends AbstractDocumentSum
{

    /**
     * Method for create a new Credit Note.
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

        if (Sales.CREDITNOTE_JASPERACTIVATION.get().contains(JasperActivation.ONCREATE)) {
            final File file = createReport(_parameter, createdDoc);
            if (file != null) {
                ret.put(ReturnValues.VALUES, file);
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    @Override
    protected List<Instance> connect2Derived(final Parameter _parameter,
                                             final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final List<Instance> ret = super.connect2Derived(_parameter, _createdDoc);
        final String[] deriveds = _parameter.getParameterValues("derived");

        ParameterUtil.setParameterValues(_parameter, "invoices", deriveds);
        return ret;
    }

    /**
     * Edit.
     *
     * @param _parameter Parameter from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final EditedDoc editDoc = editDoc(_parameter);
        updatePositions(_parameter, editDoc);

        if (Sales.CREDITNOTE_JASPERACTIVATION.get().contains(JasperActivation.ONEDIT)) {
            final File file = createReport(_parameter, editDoc);
            if (file != null) {
                ret.put(ReturnValues.VALUES, file);
                ret.put(ReturnValues.TRUE, true);
            }
        }
        return ret;
    }

    @Override
    public CIType getCIType()
        throws EFapsException
    {
        return CISales.CreditNote;
    }

    /**
     * Swap.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the return
     * @throws EFapsException on error
     */
    public Return swap(final Parameter _parameter)
        throws EFapsException
    {
        final Instance inst = _parameter.getInstance();

        final PrintQuery print = new PrintQuery(inst);
        print.addAttribute(CISales.CreditNote.RateCrossTotal, CISales.CreditNote.RateCurrencyId);
        print.execute();

        final BigDecimal amount = print.getAttribute(CISales.CreditNote.RateCrossTotal);
        final Long currencyId = print.getAttribute(CISales.CreditNote.RateCurrencyId);

        final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.CreditNote2Invoice);
        attrQueryBldr.addWhereAttrEqValue(CISales.CreditNote2Invoice.FromLink, inst);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.Invoice);
        queryBldr.addWhereAttrEqValue(CISales.Invoice.Status, Status.find(CISales.InvoiceStatus.Open));
        queryBldr.addWhereAttrInQuery(CISales.Invoice.ID,
                        attrQueryBldr.getAttributeQuery(CISales.CreditNote2Invoice.ToLink));
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        if (query.next()) {
            final Insert insert = new Insert(CISales.Document2Document4Swap);
            insert.add(CISales.Document2Document4Swap.FromLink, inst);
            insert.add(CISales.Document2Document4Swap.ToLink, query.getCurrentValue());
            insert.add(CISales.Document2Document4Swap.Amount, amount);
            insert.add(CISales.Document2Document4Swap.CurrencyLink, currencyId);
            insert.add(CISales.Document2Document4Swap.Date, new DateTime().withTimeAtStartOfDay());
            insert.execute();

            final Insert insert2 = new Insert(CISales.Document2Document4Swap);
            insert2.add(CISales.Document2Document4Swap.FromLink, query.getCurrentValue());
            insert2.add(CISales.Document2Document4Swap.ToLink, inst);
            insert2.add(CISales.Document2Document4Swap.Amount, amount);
            insert2.add(CISales.Document2Document4Swap.CurrencyLink, currencyId);
            insert2.add(CISales.Document2Document4Swap.Date, new DateTime().withTimeAtStartOfDay());
            insert2.execute();

            new DocumentUpdate().updateDocument(_parameter, inst);
            new DocumentUpdate().updateDocument(_parameter, query.getCurrentValue());
        }
        return new Return();
    }
}
