/*
 * Copyright 2003 - 2012 The eFaps Team
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

package org.efaps.esjp.sales.payment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

/**
 * TODO comment!
 * 
 * @author The eFaps Team
 * @version $Id: Payment_Base.java 7671 2012-06-14 17:25:53Z
 *          jorge.cueva@moxter.net $
 */
@EFapsUUID("c7281e33-540f-4db1-bcc6-38e89528883f")
@EFapsRevision("$Rev$")
public abstract class AbstractPaymentDocument_Base
    extends CommonDocument
{

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return CreatedDoc instance
     * @throws EFapsException on error
     */
    protected CreatedDoc createDoc(final Parameter _parameter)
        throws EFapsException
    {
        final Insert insert = new Insert(getType4DocCreate(_parameter));
        final CreatedDoc createdDoc = new CreatedDoc();

        insert.add(CISales.PaymentDocumentAbstract.Name, getDocName4Create(_parameter));

        final String note = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Note.name));
        if (note != null) {
            insert.add(CISales.PaymentDocumentAbstract.Note, note);
            createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.Note.name),
                            note);
        }

        final String amount = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Amount.name));
        if (amount != null) {
            insert.add(CISales.PaymentDocumentAbstract.Amount, amount);
            createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.Note.name),
                            amount);
        }

        final String date = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Date.name));
        if (date != null) {
            insert.add(CISales.PaymentDocumentAbstract.Date, date);
            createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.Date.name),
                            date);
        }

        final String currencyLink = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.CurrencyLink.name));
        if (currencyLink != null) {
            insert.add(CISales.PaymentDocumentAbstract.CurrencyLink, currencyLink);
            createdDoc.getValues().put(
                            getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.CurrencyLink.name),
                            currencyLink);
        }

        final String contact = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Contact.name));
        if (contact != null && Instance.get(contact).isValid()) {
            insert.add(CISales.PaymentDocumentAbstract.Contact, Instance.get(contact).getId());
            createdDoc.getValues().put(
                            getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.Contact.name),
                            Instance.get(contact).getId());
        }

        addStatus2DocCreate(_parameter, insert, createdDoc);
        add2DocCreate(_parameter, insert, createdDoc);
        insert.execute();

        createdDoc.setInstance(insert.getInstance());

        return createdDoc;
    }

    /**
     * @param _parameter
     * @param _createdDoc
     */
    protected void createPayment(final Parameter _parameter,
                                 final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final String[] createDocument = _parameter.getParameterValues(getFieldName4Attribute(_parameter,
                        CISales.Payment.CreateDocument.name));
        final String[] paymentAmount = _parameter.getParameterValues("paymentAmount");

        for (int i = 0; i < getPaymentCount(_parameter); i++) {

            final Insert payInsert = new Insert(getPaymentType(_parameter, _createdDoc));
            if (createDocument.length > i && createDocument[i] != null) {
                final Instance inst = Instance.get(createDocument[i]);
                if (inst.isValid()) {
                    payInsert.add(CISales.Payment.CreateDocument, inst.getId());
                }
            }
            if (paymentAmount.length > i && paymentAmount[i] != null) {
                payInsert.add(CISales.Payment.Amount, paymentAmount[i]);
            }
            payInsert.add(CISales.Payment.TargetDocument, _createdDoc.getInstance().getId());
            payInsert.add(CISales.Payment.CurrencyLink,
                            _createdDoc.getValues().get(getFieldName4Attribute(_parameter,
                                            CISales.PaymentDocumentAbstract.CurrencyLink.name)));
            payInsert.add(CISales.Payment.Date,
                            _createdDoc.getValues().get(getFieldName4Attribute(_parameter,
                                            CISales.PaymentDocumentAbstract.Date.name)));
            add2PaymentCreate(_parameter, payInsert, _createdDoc, i);
            payInsert.execute();
        }
    }

    protected Type getPaymentType(final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
    {
        return CISales.Payment.getType();
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return number of positions
     * @throws EFapsException on error
     */
    protected int getPaymentCount(final Parameter _parameter)
        throws EFapsException
    {
        final String[] countAr = _parameter.getParameterValues(getFieldName4Attribute(_parameter,
                        CISales.Payment.CreateDocument.name));
        return countAr == null ? 0 : countAr.length;
    }

    /**
     * Method is calles in the preocess of creation
     * 
     * @param _parameter Parameter as passed by the eFaps API
     * @param _posInsert insert to add to
     * @param _createdDoc document created
     */
    protected void add2PaymentCreate(final Parameter _parameter,
                                     final Insert _payInsert,
                                     final CreatedDoc _createdDoc,
                                     final int _idx)
    {
        // used by implementation
    }

    public Return autoComplete4CreateDocument(final Parameter _parameter)
        throws EFapsException
    {
        final String input = (String) _parameter.get(ParameterValues.OTHERS);
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();

        for (int i = 0; i < 100; i++) {
            if (props.containsKey("Type" + i)) {
                final Map<String, Map<String, String>> tmpMap = new TreeMap<String, Map<String, String>>();
                final Type type = Type.get(String.valueOf(props.get("Type" + i)));
                final QueryBuilder queryBldr = new QueryBuilder(type);
                queryBldr.addWhereAttrMatchValue(CISales.DocumentAbstract.Name, input + "*");

                add2QueryBldr4autoComplete4CreateDocument(_parameter, queryBldr);

                if (props.containsKey("StatusGroup" + i)) {
                    final String statiStr = String.valueOf(props.get("Stati" + i));
                    final String[] statiAr = statiStr.split(";");
                    final List<Object> statusList = new ArrayList<Object>();
                    for (final String stati : statiAr) {
                        final Status status = Status.find((String) props.get("StatusGroup" + i), stati);
                        if (status != null) {
                            statusList.add(status.getId());
                        }
                    }
                    queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.StatusAbstract, statusList.toArray());
                }

                final MultiPrintQuery multi = queryBldr.getPrint();
                multi.addAttribute(CISales.DocumentAbstract.OID,
                                CISales.DocumentAbstract.Name,
                                CISales.DocumentAbstract.Date);
                multi.execute();
                while (multi.next()) {
                    final String name = multi.<String>getAttribute(CISales.DocumentAbstract.Name);
                    final String oid = multi.<String>getAttribute(CISales.DocumentAbstract.OID);
                    final DateTime date = multi.<DateTime>getAttribute(CISales.DocumentAbstract.Date);

                    final StringBuilder choice = new StringBuilder()
                                    .append(name).append(" - ").append(Instance.get(oid).getType().getLabel())
                                    .append(" - ").append(date.toString(DateTimeFormat.forStyle("S-").withLocale(
                                                    Context.getThreadContext().getLocale())));
                    final Map<String, String> map = new HashMap<String, String>();
                    map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), oid);
                    map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
                    map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice.toString());
                    tmpMap.put(name, map);
                }
                list.addAll(tmpMap.values());
            } else {
                break;
            }
        }

        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    public Return updateFields4CreateDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();

        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _queryBldr queryBuilder to add to
     */
    protected void add2QueryBldr4autoComplete4CreateDocument(final Parameter _parameter,
                                                             final QueryBuilder _queryBldr)
    {
        // used bt implementation
    }

}
