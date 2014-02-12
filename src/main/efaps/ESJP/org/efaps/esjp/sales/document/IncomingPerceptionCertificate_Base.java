/*
 * Copyright 2003 - 2014 The eFaps Team
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
 * Revision:        $Rev$
 * Last Changed:    $Date$
 * Last Changed By: $Author$
 */


package org.efaps.esjp.sales.document;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.sales.Calculator;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */

@EFapsUUID("7a5f02e9-4e72-46a7-9334-48abca41d026")
@EFapsRevision("$Rev$")
public abstract class IncomingPerceptionCertificate_Base
    extends DocumentSum
{

    /**
     * Used to store the PerceptionValue in the Context.
     */
    public static final String PERCEPTIONVALUE = IncomingPerceptionCertificate.class.getName() + ".PerceptionValue";

    /**
     * Executed from a Command execute vent to create a new Incoming PerceptionCertificate.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final BigDecimal perception = parseBigDecimal(_parameter
                        .getParameterValue(CIFormSales.Sales_IncomingPerceptionCertificateForm.crossTotal.name));
        if (perception.compareTo(BigDecimal.ZERO) > 0) {
            createDoc(_parameter);
        }
        return new Return();
    }


    /**
     * Executed from a Command execute event to edit.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return edit(final Parameter _parameter)
        throws EFapsException
    {
        final BigDecimal perception = parseBigDecimal(_parameter
                        .getParameterValue(CIFormSales.Sales_IncomingPerceptionCertificateForm.crossTotal.name));
        if (perception.compareTo(BigDecimal.ZERO) > 0) {
            editDoc(_parameter);
        }
        return new Return();
    }

    /**
     * Executed from a Command execute vent to create a new Incoming PerceptionCertificate.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _createdDoc as CreatedDoc with values.
     * @throws EFapsException on error
     */
    public void create4Doc(final Parameter _parameter,
                           final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final BigDecimal perception = (BigDecimal) _createdDoc.getValue(PERCEPTIONVALUE);

        if (perception.compareTo(BigDecimal.ZERO) > 0) {

            final Insert insert = new Insert(CISales.IncomingPerceptionCertificate);
            insert.add(CISales.IncomingPerceptionCertificate.Date,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Date.name));
            insert.add(CISales.IncomingPerceptionCertificate.Contact,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Contact.name));
            insert.add(CISales.IncomingPerceptionCertificate.Salesperson,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Salesperson.name));
            insert.add(CISales.IncomingPerceptionCertificate.Group,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Group.name));
            insert.add(CISales.IncomingPerceptionCertificate.Rate,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Rate.name));
            insert.add(CISales.IncomingPerceptionCertificate.CurrencyId,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.CurrencyId.name));
            insert.add(CISales.IncomingPerceptionCertificate.RateCurrencyId,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.RateCurrencyId.name));
            insert.add(CISales.IncomingPerceptionCertificate.Creator,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Creator.name));
            insert.add(CISales.IncomingPerceptionCertificate.Created,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Created.name));
            insert.add(CISales.IncomingPerceptionCertificate.Modifier,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Modifier.name));
            insert.add(CISales.IncomingPerceptionCertificate.Modified,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Modified.name));
            insert.add(CISales.IncomingPerceptionCertificate.RateNetTotal, BigDecimal.ZERO);
            insert.add(CISales.IncomingPerceptionCertificate.RateDiscountTotal, BigDecimal.ZERO);
            insert.add(CISales.IncomingPerceptionCertificate.NetTotal, BigDecimal.ZERO);
            insert.add(CISales.IncomingPerceptionCertificate.DiscountTotal, BigDecimal.ZERO);
            insert.add(CISales.IncomingPerceptionCertificate.StatusAbstract,
                            Status.find(CISales.IncomingPerceptionCertificateStatus.Open));
            insert.add(CISales.IncomingPerceptionCertificate.Name,
                            _createdDoc.getValue(CISales.DocumentSumAbstract.Name.name));

            final Object[] rateObj = (Object[]) _createdDoc.getValue(CISales.DocumentSumAbstract.Rate.name);
            final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                            BigDecimal.ROUND_HALF_UP);
            final DecimalFormat totalFrmt = NumberFormatter.get().getFrmt4Total(getTypeName4SysConf(_parameter));
            final int scale = totalFrmt.getMaximumFractionDigits();

            insert.add(CISales.IncomingPerceptionCertificate.RateCrossTotal,
                            perception.setScale(scale, BigDecimal.ROUND_HALF_UP));
            insert.add(CISales.DocumentSumAbstract.CrossTotal,
                            perception.divide(rate, BigDecimal.ROUND_HALF_UP).setScale(scale, BigDecimal.ROUND_HALF_UP));

            insert.execute();
            final Insert relInsert = new Insert(CISales.IncomingPerceptionCertificate2IncomingInvoice);
            relInsert.add(CISales.IncomingPerceptionCertificate2IncomingInvoice.FromLink, insert.getInstance());
            relInsert.add(CISales.IncomingPerceptionCertificate2IncomingInvoice.ToLink, _createdDoc.getInstance());
            relInsert.execute();
        }
    }

    /**
     * Method to validate document connect.
     *
     * @param _parameter Parameter passed from the eFaps API.
     * @return Return values.
     * @throws EFapsException on error.
     */
    public Return validateConnectDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<?, ?> others = (HashMap<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final StringBuilder html = new StringBuilder();
        final String[] childOids = (String[]) others.get("selectedRow");
        boolean validate = true;

        if (childOids != null) {
            final Instance callInstance = _parameter.getCallInstance();
            for (final String childOid : childOids) {
                final Instance child = Instance.get(childOid);
                if (callInstance.getType().isKindOf(CISales.IncomingPerceptionCertificate.getType())) {
                    if (child.getType().equals(CISales.IncomingInvoice.getType())
                                    && check4Relation(CISales.IncomingPerceptionCertificate2IncomingInvoice.uuid, child)
                                                    .next()) {
                        validate = false;
                        html.append(getString4ReturnInvalidate(child));
                        break;
                    }
                }
            }
            if (validate) {
                ret.put(ReturnValues.TRUE, true);
                html.append(DBProperties.getProperty("org.efaps.esjp.sales.document.ValidateConnectDoc"));
                ret.put(ReturnValues.SNIPLETT, html.toString());
            } else {
                html.insert(0, DBProperties.getProperty("org.efaps.esjp.sales.document.InvalidateConnectDoc")
                                + "<p>");
                ret.put(ReturnValues.SNIPLETT, html.toString());
            }
        }
        return ret;
    }

    /**
     * Method to return name document invalidate.
     *
     * @param _child Instance.
     * @return StringBuilder to String.
     * @throws EFapsException on error.
     */
    protected StringBuilder getString4ReturnInvalidate(final Instance _child)
        throws EFapsException
    {
        final StringBuilder html = new StringBuilder();
        final PrintQuery print = new PrintQuery(_child);
        print.addAttribute(CISales.DocumentAbstract.Name);
        print.execute();
        return html.append(_child.getType().getLabel()
                        + " - " + print.<String>getAttribute(CISales.DocumentAbstract.Name));
    }

    /**
     * Check if the a relation of the given type already exists for this
     * instance.
     *
     * @param _typeUUID uuid of the ration type
     * @param _instance instance to be checked
     * @return true if already an relation of the given type exists for the
     *         instance, else false
     * @throws EFapsException on error
     */
    protected MultiPrintQuery check4Relation(final UUID _typeUUID,
                                             final Instance _instance)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(_typeUUID);
        queryBldr.addWhereAttrMatchValue(CISales.IncomingDocumentTax2Document.ToAbstractLink,
                        _instance.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CISales.IncomingDocumentTax2Document.OID);
        multi.execute();

        return multi;
    }

    /**
     * Connect Document.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return connectDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Map<?, ?> others = (HashMap<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final Map<?, ?> props = (HashMap<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String[] childOids = (String[]) others.get("selectedRow");
        if (childOids != null) {
            final Instance callInstance = _parameter.getCallInstance();
            for (final String childOid : childOids) {
                final Instance child = Instance.get(childOid);
                Insert insert = null;
                if (callInstance.getType().isKindOf(CISales.IncomingPerceptionCertificate.getType())) {
                    // defaults
                    if (child.getType().equals(CISales.IncomingInvoice.getType())) {
                        insert = new Insert(CISales.IncomingPerceptionCertificate2IncomingInvoice);
                    }
                    int i = 0;
                    while (insert == null && props.containsKey("connect" + i)) {
                        final String connectUUIDStr = (String) props.get("connect" + i);
                        final String[] connectUUIDs = connectUUIDStr.split(";");
                        if (child.getType().getUUID().equals(UUID.fromString(connectUUIDs[0]))) {
                            insert = new Insert(UUID.fromString(connectUUIDs[1]));
                        }
                        i++;
                    }
                }
                if (insert != null) {
                    insert.add(CISales.IncomingDocumentTax2Document.FromAbstractLink, callInstance.getId());
                    insert.add(CISales.IncomingDocumentTax2Document.ToAbstractLink, child.getId());
                    insert.execute();
                }
            }
        }
        return new Return();
    }

    @Override
    protected BigDecimal getCrossTotal(final Parameter _parameter,
                                       final List<Calculator> _calcList)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        if (_calcList.isEmpty()) {
            ret = ret.add(parseBigDecimal(_parameter
                            .getParameterValue(CIFormSales.Sales_IncomingPerceptionCertificateForm.crossTotal.name)));
        } else {
            ret = super.getCrossTotal(_parameter, _calcList);
        }

        return ret;
    }

    protected BigDecimal parseBigDecimal(final String _value)
        throws EFapsException
    {
        final DecimalFormat formater = NumberFormatter.get().getFormatter();
        BigDecimal ret = BigDecimal.ZERO;
        try {
            if (_value != null && !_value.isEmpty()) {
                ret = (BigDecimal) formater.parse(_value);
            }
        } catch (final ParseException p) {
            throw new EFapsException(IncomingPerceptionCertificate.class, "RateCrossTotal.ParseException", p);
        }
        return ret;
    }
}
