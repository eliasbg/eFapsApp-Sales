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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Delete;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.contacts.Contacts;
import org.efaps.esjp.sales.Account;
import org.efaps.esjp.sales.Calculator;
import org.efaps.esjp.sales.Transaction;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("9b1b92aa-550a-48d3-a58e-2c47e54802f9")
@EFapsRevision("$Rev$")
public abstract class PettyCashReceipt_Base
    extends DocumentSum
{

    /**
     * Executed from a Command execute vent to create a new PettyCashReceipt.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPositions(_parameter, createdDoc);
        connect2DocumentType(_parameter, createdDoc);
        connect2Account(_parameter, createdDoc);
        createTransaction(_parameter, createdDoc);
        return new Return();
    }

    /**
     * Connect Account and PettyCash.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _createdDoc doc the account is connected to
     * @throws EFapsException on error
     */
    protected void connect2Account(final Parameter _parameter,
                                   final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Insert insert = new Insert(CISales.AccountPettyCash2PettyCashReceipt);
        insert.add(CISales.AccountPettyCash2PettyCashReceipt.FromLink, _parameter.getInstance());
        insert.add(CISales.AccountPettyCash2PettyCashReceipt.ToLink, _createdDoc.getInstance());
        insert.execute();
    }

    /**
     * Create the transaction for the PettyCash.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _createdDoc doc the transaction is connected to
     * @throws EFapsException on error
     */
    protected void createTransaction(final Parameter _parameter,
                                     final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Insert payInsert = new Insert(CISales.Payment);
        payInsert.add(CISales.Payment.Date, _createdDoc.getValue(CISales.DocumentSumAbstract.Date.name));
        payInsert.add(CISales.Payment.CreateDocument, _createdDoc.getInstance());
        payInsert.execute();

        final Insert transInsert = new Insert(CISales.TransactionOutbound);
        transInsert.add(CISales.TransactionOutbound.Amount,
                        _createdDoc.getValue(CISales.DocumentSumAbstract.RateCrossTotal.name));
        transInsert.add(CISales.TransactionOutbound.CurrencyId,
                        _createdDoc.getValue(CISales.DocumentSumAbstract.RateCurrencyId.name));
        transInsert.add(CISales.TransactionOutbound.Payment, payInsert.getInstance());
        transInsert.add(CISales.TransactionOutbound.Account, _parameter.getInstance());
        transInsert.add(CISales.TransactionOutbound.Description,
                        _createdDoc.getValue(CISales.DocumentSumAbstract.Note.name));
        transInsert.add(CISales.TransactionOutbound.Date, _createdDoc.getValue(CISales.DocumentSumAbstract.Date.name));
        transInsert.execute();
    }

    /**
     * Update the transaction for the PettyCash.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @param _editedDoc doc the transaction is connected to
     * @param _prevAmount previous amount
     * @throws EFapsException on error
     */
    protected void updateTransaction(final Parameter _parameter,
                                     final EditedDoc _editedDoc)
        throws EFapsException
    {
        // get the payment
        final QueryBuilder payQueryBldr = new QueryBuilder(CISales.Payment);
        payQueryBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, _editedDoc.getInstance());
        final InstanceQuery payQuery = payQueryBldr.getQuery();
        payQuery.executeWithoutAccessCheck();
        if (payQuery.next()) {
            final QueryBuilder transQueryBldr = new QueryBuilder(CISales.TransactionOutbound);
            transQueryBldr.addWhereAttrEqValue(CISales.TransactionOutbound.Payment, payQuery.getCurrentValue());
            final MultiPrintQuery multi = transQueryBldr.getPrint();
            final SelectBuilder accSel = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account).instance();
            final SelectBuilder curSel = SelectBuilder.get().linkto(CISales.TransactionAbstract.CurrencyId).instance();
            multi.addSelect(accSel, curSel);
            multi.addAttribute(CISales.TransactionOutbound.Amount);
            multi.executeWithoutAccessCheck();
            if (multi.next()) {
                final BigDecimal amount = multi.<BigDecimal>getAttribute(CISales.TransactionAbstract.Amount);
                final Instance accountInst = multi.<Instance>getSelect(accSel);
                final Instance currencyInst = multi.<Instance>getSelect(curSel);
                final BigDecimal newAmount = (BigDecimal) _editedDoc
                                .getValue(CISales.DocumentSumAbstract.RateCrossTotal.name);
                if (newAmount.compareTo(amount) != 0) {
                    final Update update = new Update(multi.getCurrentInstance());
                    update.add(CISales.TransactionOutbound.Amount,
                                    _editedDoc.getValue(CISales.DocumentSumAbstract.RateCrossTotal.name));
                    update.add(CISales.TransactionOutbound.CurrencyId,
                                    _editedDoc.getValue(CISales.DocumentSumAbstract.RateCurrencyId.name));
                    update.add(CISales.TransactionOutbound.Description,
                                    _editedDoc.getValue(CISales.DocumentSumAbstract.Note.name));
                    update.execute();
                    new Transaction().updateBalance(_parameter, accountInst, currencyInst, newAmount.subtract(amount));
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getDocName4Create(final Parameter _parameter)
        throws EFapsException
    {
        String ret = _parameter.getParameterValue("name4create");
        if (ret == null || (ret != null && ret.isEmpty())) {
            final PrintQuery print = new PrintQuery(_parameter.getInstance());
            print.addAttribute(CISales.AccountPettyCash.Name);
            print.execute();
            final String accName = print.<String>getAttribute(CISales.AccountPettyCash.Name);
            ret =  accName + " " + (new Account().getMaxPosition(_parameter, _parameter.getInstance()) + 1);
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return html for display and true or false
     * @throws EFapsException on errro
     */
    public Return validate(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        final boolean evaluatePostions = !"false".equalsIgnoreCase(getProperty(_parameter, "EvaluatePostions"));
        // first check the positions
        final List<Calculator> calcList = analyseTable(_parameter, null);
        if (evaluatePostions
                        && (calcList.isEmpty() || getNetTotal(_parameter, calcList).compareTo(BigDecimal.ZERO) == 0)) {
            html.append(DBProperties.getProperty(PettyCashReceipt.class.getName() + ".validate4Positions"));
        } else {
            if (evalDeducible(_parameter)) {
                final Return tmp = validateName(_parameter);
                final String snipplet = (String) tmp.get(ReturnValues.SNIPLETT);
                if (snipplet != null) {
                    html.append(snipplet);
                }
                final String name = _parameter
                                .getParameterValue(CIFormSales.Sales_PettyCashReceiptForm.name4create.name);
                final Instance contactInst = Instance.get(_parameter
                                .getParameterValue(CIFormSales.Sales_PettyCashReceiptForm.contact.name));
                if (name != null && !name.isEmpty() && contactInst.isValid()) {
                    ret.put(ReturnValues.TRUE, true);
                } else {
                    html.append(DBProperties.getProperty(PettyCashReceipt.class.getName() + ".validate4Deducible"));
                }
            } else {
                final String name = _parameter
                                .getParameterValue(CIFormSales.Sales_PettyCashReceiptForm.name4create.name);
                final String contact = _parameter
                                .getParameterValue(CIFormSales.Sales_PettyCashReceiptForm.contact.name);
                if ((name == null || name.isEmpty()) && (contact == null || contact.isEmpty())) {
                    ret.put(ReturnValues.TRUE, true);
                } else {
                    html.append(DBProperties.getProperty(PettyCashReceipt.class.getName() + ".validate4NotDeducible"));
                }
            }
        }
        if (html.length() > 0) {
            ret.put(ReturnValues.SNIPLETT, html.toString());
        }
        return ret;
    }

    /**
     *  @param _parameter Parameter from the eFaps API.
     * @return new Return.
     * @throws EFapsException on error.
     */
    public Return update4DocumentType(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        if (!evalDeducible(_parameter)) {
            final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            final Map<String, String> map = new HashMap<String, String>();
            list.add(map);
            map.put(CIFormSales.Sales_PettyCashReceiptForm.name4create.name, "");
            map.put(CIFormSales.Sales_PettyCashReceiptForm.contactData.name, "");
            map.put(CIFormSales.Sales_PettyCashReceiptForm.contact.name, "");
            map.put(CIFormSales.Sales_PettyCashReceiptForm.contact.name + "AutoComplete", "");
            retVal.put(ReturnValues.VALUES, list);
        }
        return retVal;
    }

    /**
     * @param _parameter Paramater as passed by the eFaps APi
     * @return true if deducible else false
     */
    protected boolean evalDeducible(final Parameter _parameter)
    {
        return !"NONE".equals(_parameter.getParameterValue(CIFormSales.Sales_PettyCashReceiptForm.documentType.name));
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
        final EditedDoc editDoc = editDoc(_parameter);
        updatePositions(_parameter, editDoc);
        updateTransaction(_parameter, editDoc);
        return new Return();
    }
    /**
     * @param _parameter parameter as passed by the eFaps API
     * @return Return contiaining javascript
     * @throws EFapsException on error
     */
    public Return getJavaScriptUIValue4EditJustification(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Instance inst = _parameter.getCallInstance();
        final StringBuilder js = new StringBuilder();
        js.append("<script type=\"text/javascript\">")
                        .append("Wicket.Event.add(window, \"domready\", function(event) {");

        if (inst.isValid()) {
            final PrintQuery print = new PrintQuery(inst);
            final SelectBuilder selContInst = new SelectBuilder().linkto(CISales.PettyCashReceipt.Contact).instance();
            final SelectBuilder selContName = new SelectBuilder().linkto(CISales.PettyCashReceipt.Contact)
                            .attribute(CIContacts.Contact.Name);
            print.addSelect(selContInst, selContName);
            print.addAttribute(CISales.PettyCashReceipt.Name);

            if (print.execute()) {
                final Instance contInst = print.<Instance>getSelect(selContInst);
                if (contInst.isValid()) {
                    final QueryBuilder queryBldr = new QueryBuilder(CISales.Document2DocumentType);
                    queryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentLink, inst);
                    final MultiPrintQuery multi = queryBldr.getPrint();
                    final SelectBuilder selDocTypeInst = new SelectBuilder().linkto(
                                    CISales.Document2DocumentType.DocumentTypeLink).instance();
                    multi.addSelect(selDocTypeInst);
                    multi.execute();
                    if (multi.next()) {
                        js.append(getSetFieldValue(0,
                                        CIFormSales.Sales_PettyCashReceiptJustificationEditForm.documentType.name,
                                        multi.<Instance>getSelect(selDocTypeInst).getOid()));
                    }
                    final String info = new Contacts().getFieldValue4Contact(contInst, false);
                    final String contName = print.<String>getSelect(selContName);
                    final String name = print.<String>getAttribute(CISales.PettyCashReceipt.Name);

                    js.append(getSetFieldValue(0,
                                    CIFormSales.Sales_PettyCashReceiptJustificationEditForm.contactData.name, info))
                        .append(getSetFieldValue(0,
                                    CIFormSales.Sales_PettyCashReceiptJustificationEditForm.contact.name
                                                                    + "AutoComplete", contName))
                        .append(getSetFieldValue(0,
                                    CIFormSales.Sales_PettyCashReceiptJustificationEditForm.contact.name,
                                                    contInst.getOid()))
                        .append(getSetFieldValue(0,
                                    CIFormSales.Sales_PettyCashReceiptJustificationEditForm.name4create.name, name));
                }
            }
            js.append(" });").append("</script>");
        }
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }

    /**
     * @param _parameter parameter as passed by the eFaps API
     * @return new empty Return
     * @throws EFapsException on error
     */
    public Return editJustification(final Parameter _parameter)
        throws EFapsException
    {
        final boolean deducible = evalDeducible(_parameter);

        final String contact = _parameter
                        .getParameterValue(CIFormSales.Sales_PettyCashReceiptJustificationEditForm.contact.name);
        final String docName = _parameter
                        .getParameterValue(CIFormSales.Sales_PettyCashReceiptJustificationEditForm.name4create.name);
        final String docType = _parameter
                        .getParameterValue(CIFormSales.Sales_PettyCashReceiptJustificationEditForm.documentType.name);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.Document2DocumentType);
        queryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentLink, _parameter.getCallInstance());
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        Instance docTypeRelInst = null;
        if (query.next()) {
            docTypeRelInst = query.getCurrentValue();
        }

        final Update update = new Update(_parameter.getCallInstance());
        if (deducible) {
            update.add(CISales.PettyCashReceipt.Contact, Instance.get(contact));
            update.add(CISales.PettyCashReceipt.Name, docName);

            Update relUpdate;
            if (docTypeRelInst != null && docTypeRelInst.isValid()) {
                relUpdate = new Update(docTypeRelInst);
            } else {
                relUpdate = new Insert(CISales.Document2DocumentType);
                relUpdate.add(CISales.Document2DocumentType.DocumentLink, _parameter.getCallInstance());
            }
            relUpdate.add(CISales.Document2DocumentType.DocumentTypeLink, Instance.get(docType));
            relUpdate.execute();
        } else {
            update.add(CISales.PettyCashReceipt.Contact, (Object) null);
            if (docTypeRelInst != null && docTypeRelInst.isValid()) {
                new Delete(docTypeRelInst).execute();
            }
            final PrintQuery print = new PrintQuery(_parameter.getCallInstance());
            final SelectBuilder posSel = SelectBuilder.get().linkfrom(CISales.AccountPettyCash2PettyCashReceipt,
                            CISales.AccountPettyCash2PettyCashReceipt.ToLink)
                            .attribute(CISales.AccountPettyCash2PettyCashReceipt.Position);
            final SelectBuilder nameSel = SelectBuilder.get().linkfrom(CISales.AccountPettyCash2PettyCashReceipt,
                            CISales.AccountPettyCash2PettyCashReceipt.ToLink)
                            .linkto(CISales.AccountPettyCash2PettyCashReceipt.FromLink)
                            .attribute(CISales.AccountPettyCash.Name);
            print.addSelect(posSel, nameSel);
            print.execute();
            update.add(CISales.PettyCashReceipt.Name, print.getSelect(nameSel) + " " + print.getSelect(posSel));
        }
        update.execute();
        return new Return();
    }


    @Override
    public Return dropDown4DocumentType(final Parameter _parameter)
        throws EFapsException
    {
        return new org.efaps.esjp.common.uiform.Field()
        {

            @Override
            protected void updatePositionList(final Parameter _parameter,
                                              final List<DropDownPosition> _values)
                throws EFapsException
            {
                final DropDownPosition ddPos = new DropDownPosition("NONE",
                                DBProperties.getProperty(PettyCashReceipt.class.getName() + ".NONEPosition.Label"));
                _values.add(0, ddPos);
            };
        } .dropDownFieldValue(_parameter);
    }
}
