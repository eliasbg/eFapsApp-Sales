/*
 * Copyright 2003 - 2013 The eFaps Team
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringEscapeUtils;
import org.efaps.admin.common.NumberGenerator;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.RateUI;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.program.esjp.Listener;
import org.efaps.ci.CIAttribute;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Checkin;
import org.efaps.db.Context;
import org.efaps.db.Insert;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.db.Update;
import org.efaps.esjp.admin.datamodel.StatusValue;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.ci.CITableSales;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.common.parameter.ParameterUtil;
import org.efaps.esjp.common.uitable.MultiPrint;
import org.efaps.esjp.common.util.InterfaceUtils;
import org.efaps.esjp.erp.CommonDocument;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.NumberFormatter;
import org.efaps.esjp.erp.RateFormatter;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.esjp.sales.Account;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.esjp.sales.document.AbstractDocumentTax_Base.DocTaxInfo;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.esjp.sales.document.AbstractDocument_Base.KeyDef;
import org.efaps.esjp.sales.document.Conciliation;
import org.efaps.esjp.sales.document.Invoice;
import org.efaps.esjp.sales.listener.IOnPayment;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.Sales.AccountAutomation;
import org.efaps.esjp.sales.util.Sales.AccountCDActivation;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.esjp.ui.html.HtmlTable;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Logger for this class.
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractPaymentDocument.class);

    public static final String INVOICE_SESSIONKEY = "eFaps_Selected_Sales_Invoice";

    public static final String CONTACT_SESSIONKEY = "eFaps_Selected_Contact";

    public static final String CHANGE_AMOUNT = "eFaps_Selected_ChangeAmount";



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

        final String name = getDocName4Create(_parameter);
        if (name != null) {
            insert.add(CISales.PaymentDocumentAbstract.Name, name);
            createdDoc.getValues().put(getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.Name.name),
                            name);
        }

        final String note = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Note.name));
        if (note != null) {
            insert.add(CISales.PaymentDocumentAbstract.Note, note);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Note.name, note);
        }

        final String amount = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Amount.name));
        if (amount != null) {
            insert.add(CISales.PaymentDocumentAbstract.Amount, amount);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Amount.name, amount);
        }

        final String date = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Date.name));
        if (date != null) {
            insert.add(CISales.PaymentDocumentAbstract.Date, date);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Date.name, date);
        }

        final String dueDate = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.DueDate.name));
        if (dueDate != null) {
            insert.add(CISales.PaymentDocumentAbstract.DueDate, dueDate);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.DueDate.name, dueDate);
        }

        final String revision = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Revision.name));
        if (revision != null) {
            insert.add(CISales.PaymentDocumentAbstract.Revision, revision);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Revision.name, revision);
        }

        final String currencyLink = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.CurrencyLink.name));
        if (currencyLink != null) {
            insert.add(CISales.PaymentDocumentAbstract.RateCurrencyLink, currencyLink);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.RateCurrencyLink.name,
                            Long.parseLong(currencyLink));
            final Instance baseCurrInst = Currency.getBaseCurrency();
            insert.add(CISales.PaymentDocumentAbstract.CurrencyLink, baseCurrInst.getId());
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.CurrencyLink.name,
                            baseCurrInst.getId());
        }

        final String currencyLink4Account = getRateCurrencyLink4Account(_parameter);
        if (currencyLink4Account != null) {
            insert.add(CISales.PaymentDocumentAbstract.RateCurrencyLink, currencyLink4Account);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.RateCurrencyLink.name,
                            Long.parseLong(currencyLink4Account));
            final Instance baseCurrInst = Currency.getBaseCurrency();
            insert.add(CISales.PaymentDocumentAbstract.CurrencyLink, baseCurrInst.getId());
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.CurrencyLink.name,
                            baseCurrInst.getId());
        }

        final String contact = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Contact.name));
        if (contact != null && Instance.get(contact).isValid()) {
            insert.add(CISales.PaymentDocumentAbstract.Contact, Instance.get(contact).getId());
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Contact.name,
                            Instance.get(contact).getId());
        }

        final Object rateObj = _parameter.getParameterValue("rate");
        if (rateObj != null) {
            insert.add(CISales.PaymentDocumentAbstract.Rate, getRateObject(_parameter));
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Rate.name, getRateObject(_parameter));
        }

        final String code = getCode4GeneratedDocWithSysConfig(_parameter);
        if (code != null) {
            insert.add(CISales.PaymentDocumentAbstract.Code, code);
            createdDoc.getValues().put(CISales.PaymentDocumentAbstract.Code.name, code);
        }

        addStatus2DocCreate(_parameter, insert, createdDoc);
        add2DocCreate(_parameter, insert, createdDoc);
        insert.execute();

        createdDoc.setInstance(insert.getInstance());

        return createdDoc;
    }

    /**
     * @param _parameter Paramter as passed by the eFaps API
     * @param _paymentDocInst instance of the payment document
     * @return Sales.AccountAutomation
     * @throws EFapsException on error
     */
    public AccountAutomation evaluateAutomation(final Parameter _parameter,
                                                final Instance _paymentDocInst)
        throws EFapsException
    {
        AccountAutomation ret =  AccountAutomation.NONE;
        final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Payment);
        attrQueryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, _paymentDocInst);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.Payment.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.TransactionAbstract);
        queryBldr.addWhereAttrInQuery(CISales.TransactionAbstract.Payment, attrQuery);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selAccInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account).instance();
        multi.addSelect(selAccInst);
        multi.execute();
        final Set<Instance> accInsts = new HashSet<>();
        while (multi.next()) {
            accInsts.add(multi.<Instance>getSelect(selAccInst));
        }
        AccountAutomation auto = null;
        for (final Instance accInst : accInsts) {
            if (!accInst.getType().isKindOf(CISales.AccountCashDesk.getType())) {
                auto = AccountAutomation.NONE;
                break;
            }
            final PrintQuery print = new CachedPrintQuery(accInst, Account.CACHEKEY);
            print.addAttribute(CISales.AccountCashDesk.Automation);
            print.executeWithoutAccessCheck();
            final AccountAutomation autoTmp = print.getAttribute(CISales.AccountCashDesk.Automation);
            if (auto == null) {
                if (autoTmp == null) {
                    auto = AccountAutomation.NONE;
                } else {
                    auto = autoTmp;
                }
            }
            switch (auto) {
                // if none was set it cannot be unset
                case NONE:
                    break;
                // if conciliation should be done automatic
                case CONCILIATION:
                    switch (autoTmp) {
                        case NONE:
                        case TRANSACTION:
                            auto = AccountAutomation.NONE;
                        default:
                            break;
                    }
                    break;
                case TRANSACTION:
                    switch (autoTmp) {
                        case NONE:
                        case CONCILIATION:
                            auto = AccountAutomation.NONE;
                        default:
                            break;
                    }
                    break;
                case FULL:
                    auto = autoTmp;
                default:
                    auto = autoTmp;
                    break;
            }
        }
        if  (auto != null) {
            ret = auto;
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _createdDoc doc
     * @throws EFapsException on error
     */
    protected void executeAutomation(final Parameter _parameter,
                                     final CreatedDoc _createdDoc)
        throws EFapsException
    {
        final Instance paymentDocInst = _createdDoc.getInstance();
        final AccountAutomation auto = evaluateAutomation(_parameter, paymentDocInst);
        switch (auto) {
            case FULL:
            case CONCILIATION:
                new Conciliation().createPosition4Automation(_parameter, paymentDocInst);
                final Status payStatus = Status.find(paymentDocInst.getType().getStatusAttribute().getLink().getUUID(),
                                "Closed");
                if (payStatus != null) {
                    final Update update = new Update(paymentDocInst);
                    update.add(CISales.PaymentDocumentIOAbstract.StatusAbstract, payStatus);
                    update.execute();
                }
                break;
            default:
                break;
        }
        for (final IOnPayment listener : Listener.get().<IOnPayment>invoke(IOnPayment.class)) {
            listener.executeAutomation(this, _parameter, _createdDoc);
        }
    }

    /**
     * Fills the paymentdocument form with the selected values.
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for picker event
     * @throws EFapsException on error
     */
    public Return picker4Document(final Parameter _parameter)
        throws EFapsException
    {
        final Return retVal = new Return();
        final Map<String, String> map = new HashMap<>();
        retVal.put(ReturnValues.VALUES, map);

        final StringBuilder js = new StringBuilder();
        js.append(getTableRemoveScript(_parameter, "paymentTable"));
        final List<Map<String, Object>> values = new ArrayList<>();

        final List<Instance> docInsts = new ArrayList<>();
        final String[] oids = _parameter.getParameterValues("selectedRow");
        if (oids != null) {
            for (final String oid : oids) {
                final Instance docInst = Instance.get(oid);
                if (docInst.isValid()) {
                    docInsts.add(docInst);
                }
            }
            final Parameter parameter = ParameterUtil.clone(_parameter, ParameterValues.PARAMETERS,
                            _parameter.get(ParameterValues.PARENTPARAMETERS));
            for (final Instance docInst : docInsts) {
                values.add(getPositionUpdateMap(parameter, docInst, true));
            }
        }
        final List<Map<String, Object>> sums = new ArrayList<>();
        sums.add(getSumUpdateMap(_parameter, values, false));
        js.append(getSetFieldValuesScript(_parameter, sums, null));
        js.append(getTableAddNewRowsScript(_parameter, "paymentTable", values, null));
        map.put(EFapsKey.PICKER_JAVASCRIPT.getKey(), js.toString());
        return retVal;
    }

    /**
     * Multiprint used to fill the table for the picker.
     * @param _parameter Parameter as passed by the eFaps API
     * @return map for picker event
     * @throws EFapsException on error
     */
    public Return pickerMultiPrint(final Parameter _parameter)
        throws EFapsException
    {
        final MultiPrint mulit = new MultiPrint() {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));
                final boolean deactFilter = "true".equalsIgnoreCase(_parameter.getParameterValue("checkbox4Invoice"));
                if (contactInst.isValid() && !deactFilter) {
                    _queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.Contact, contactInst.getId());
                } else if (!deactFilter) {
                    // show nothing
                    _queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.ID, 0);
                }
                super.add2QueryBldr(_parameter, _queryBldr);
            }
        };
        return mulit.execute(_parameter);
    }

    /**
     * @param _parameter parameter as passed by the eFasp API
     * @param _docInst instance of the document the map is wanted for
     * @param _addDocInfo add the autocomplete field also
     * @return map containing values for update
     * @throws EFapsException on error
     */
    protected Map<String, Object> getPositionUpdateMap(final Parameter _parameter,
                                                       final Instance _docInst,
                                                       final boolean _addDocInfo)
        throws EFapsException
    {
        final Instance accInst = Instance.get(CISales.AccountCashDesk.getType(),
                        _parameter.getParameterValue("account"));
        final DocPaymentInfo docInfo = getNewDocPaymentInfo(_parameter, _docInst);
        docInfo.setAccountInst(accInst);
        return getPositionUpdateMap(_parameter, docInfo, _addDocInfo);
    }

    /**
     * @param _parameter parameter as passed by the eFasp API
     * @param _docInfo DocPaymentInfo the map is wanted for
     * @param _addDocInfo add the autocomplete field also
     * @return map containing values for update
     * @throws EFapsException on error
     */
    protected Map<String, Object> getPositionUpdateMap(final Parameter _parameter,
                                                       final DocPaymentInfo _docInfo,
                                                       final boolean _addDocInfo)
        throws EFapsException
    {
        final Map<String, Object> ret = new HashMap<>();

        final DecimalFormat frmt = _docInfo.getFormatter();
        final BigDecimal total4Doc = _docInfo.getCrossTotal4Target();
        final BigDecimal payments4Doc = _docInfo.getPaid4Target();
        final BigDecimal amount4PayDoc;
        final BigDecimal paymentDiscount;
        final BigDecimal paymentAmountDesc;
        if (payments4Doc.compareTo(BigDecimal.ZERO) == 0) {
            // if this is the first payment. check for detraction etc.
            final DocTaxInfo docTaxInfo = _docInfo.getDocTaxInfo();
            amount4PayDoc = total4Doc.subtract(docTaxInfo.getTaxAmount());
            paymentDiscount = docTaxInfo.getPercent();
            paymentAmountDesc = docTaxInfo.getTaxAmount();
        } else {
            amount4PayDoc = total4Doc.subtract(payments4Doc);
            paymentDiscount = BigDecimal.ZERO;
            paymentAmountDesc = BigDecimal.ZERO;
        }
        if (_addDocInfo) {
            ret.put("createDocument", new String[] { _docInfo.getInstance().getOid(), _docInfo.getName() });
        }
        ret.put("createDocumentContact", _docInfo.getContactName());
        ret.put("createDocumentDesc", _docInfo.getInfoField());
        ret.put("payment4Pay", frmt.format(amount4PayDoc));
        ret.put("paymentAmount", frmt.format(amount4PayDoc));
        ret.put("paymentAmountDesc", frmt.format(paymentAmountDesc));
        ret.put("paymentDiscount", frmt.format(paymentDiscount));
        ret.put("paymentRate", _docInfo.getRateInfo4Target().getRateUIFrmt());
        ret.put("paymentRate" + RateUI.INVERTEDSUFFIX, "" + _docInfo.getRateInfo4Target().isInvert());
        return ret;
    }

    /**
     * @param _parameter parameter as passed by the eFasp API
     * @param _maps list of maps to be analyzed for the sum
     * @param _includeUI inlude the values from the UI also
     * @return map of sum
     * @throws EFapsException on error
     */
    protected Map<String, Object> getSumUpdateMap(final Parameter _parameter,
                                                  final Collection<Map<String, Object>> _maps,
                                                  final boolean _includeUI)
        throws EFapsException
    {
        final Map<String, Object> ret = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (final Map<String, Object> map : _maps) {
            try {
                total = total.add((BigDecimal) NumberFormatter.get().getFormatter()
                                .parse((String) map.get("paymentAmount")));
            } catch (final ParseException e) {
                LOG.error("Catched ParseException", e);
            }
        }
        if (_includeUI) {
            total = total.add(getSum4Positions(_parameter, false));
        }
        if (Context.getThreadContext().getSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT) == null) {
            ret.put("amount", NumberFormatter.get().getTwoDigitsFormatter().format(total));
            ret.put("total4DiscountPay", NumberFormatter.get().getTwoDigitsFormatter().format(BigDecimal.ZERO));
        } else {
            final BigDecimal amount = parseBigDecimal(_parameter.getParameterValue("amount"));
            ret.put("total4DiscountPay", NumberFormatter.get().getTwoDigitsFormatter().format(amount.subtract(total)));
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return list map for fieldupdate event
     * @throws EFapsException on error
     */
    public Return updateFields4PaymentRate(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, Object>> list = new ArrayList<>();
        final int selected = getSelectedRow(_parameter);
        final Instance docInstance = Instance.get(_parameter.getParameterValues("createDocument")[selected]);
        final Instance accInstance = Instance.get(CISales.AccountCashDesk.getType(),
                        _parameter.getParameterValue("account"));
        if (docInstance.isValid() && accInstance.isValid()) {
            try {
                final Object[] rateObj = getRateObject(_parameter, "paymentRate", selected);
                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
                                BigDecimal.ROUND_HALF_UP);
                final BigDecimal rateUI = (BigDecimal) NumberFormatter.get().getFormatter()
                                .parse(_parameter.getParameterValues("paymentRate")[selected]);
                final DocPaymentInfo docInfo = getNewDocPaymentInfo(_parameter, docInstance);
                docInfo.setAccountInst(accInstance);
                docInfo.getRateInfo4Target().setRate(rate);
                docInfo.getRateInfo4Target().setRateUI(rateUI);

                list.add(getPositionUpdateMap(_parameter, docInfo, false));
                list.add(getSumUpdateMap(_parameter, list, true));
            } catch (final ParseException e) {
                LOG.error("Catched ParseException", e);
            }
        }
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return list map for fieldupdate event
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return updateFields4CreateDocument(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final int selected = getSelectedRow(_parameter);
        final Instance docInstance = Instance.get(_parameter.getParameterValues("createDocument")[selected]);
        final Map<String, Object> map = getPositionUpdateMap(_parameter, docInstance, false);
        map.putAll(getSumUpdateMap(_parameter, Arrays.<Map<String, Object>>asList(new Map[]{ map }), true));
        list.add(map);
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    /**
     * @param _parameter parameter as passed by the eFasp API
     * @param _includeCurrent include the current position
     * @return sum of the positions
     * @throws EFapsException on error
     */
    protected BigDecimal getSum4Positions(final Parameter _parameter,
                                          final boolean _includeCurrent)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        final String[] paymentAmounts = _parameter.getParameterValues("paymentAmount");
        if (paymentAmounts != null) {
            final int selected = getSelectedRow(_parameter);
            for (int i = 0; i < paymentAmounts.length; i++) {
                try {
                    if (!_includeCurrent && selected != i || _includeCurrent) {
                        ret = ret.add((BigDecimal) NumberFormatter.get().getFormatter().parse(paymentAmounts[i]));
                    }
                } catch (final ParseException e) {
                    // only show that error during debug,
                    // because it is likely that the user did just used invalid strings
                    LOG.debug("Catched ParseException", e);
                }
            }
        }
        return ret;
    }


    protected String getRateCurrencyLink4Account(final Parameter _parameter)
        throws EFapsException
    {
        long currencyId = 0;
        final String account = _parameter.getParameterValue("account");
        if (account != null) {
            final PrintQuery print = new PrintQuery(CISales.AccountCashDesk.getType(), account);
            print.addAttribute(CISales.AccountCashDesk.CurrencyLink);
            print.execute();
            currencyId = print.<Long>getAttribute(CISales.AccountCashDesk.CurrencyLink);
        }
        return currencyId == 0 ? null : String.valueOf(currencyId);
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
            Insert transIns;
            if (getType4DocCreate(_parameter) != null
                        && getType4DocCreate(_parameter).isKindOf(CISales.PaymentDocumentAbstract.getType())
                    || _parameter.getInstance() != null
                        && _parameter.getInstance().isValid()
                        && _parameter.getInstance().getType().isKindOf(CISales.PaymentDocumentAbstract.getType())) {
                transIns = new Insert(CISales.TransactionInbound);
            } else {
                transIns = new Insert(CISales.TransactionOutbound);
            }

            if (createDocument.length > i && createDocument[i] != null) {
                final Instance inst = Instance.get(createDocument[i]);
                if (inst.isValid()) {
                    payInsert.add(CISales.Payment.CreateDocument, inst.getId());
                    payInsert.add(CISales.Payment.RateCurrencyLink,
                                    getNewDocPaymentInfo(_parameter, inst).getRateCurrencyInstance());
                    _createdDoc.addPosition(inst);
                }
            }
            if (paymentAmount.length > i && paymentAmount[i] != null) {
                payInsert.add(CISales.Payment.Amount, paymentAmount[i]);
                transIns.add(CISales.TransactionAbstract.Amount, paymentAmount[i]);
            }
            payInsert.add(CISales.Payment.TargetDocument, _createdDoc.getInstance().getId());
            payInsert.add(CISales.Payment.CurrencyLink,
                            _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.RateCurrencyLink.name));
            payInsert.add(CISales.Payment.Date,
                            _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.Date.name));
            payInsert.add(CISales.Payment.Rate, getRateObject(_parameter, "paymentRate", i));
            add2PaymentCreate(_parameter, payInsert, _createdDoc, i);
            payInsert.execute();

            transIns.add(CISales.TransactionAbstract.CurrencyId,
                            _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.RateCurrencyLink.name));
            transIns.add(CISales.TransactionAbstract.Payment, payInsert.getId());
            transIns.add(CISales.TransactionAbstract.Date,
                            _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.Date.name));
            transIns.add(CISales.TransactionAbstract.Account, _parameter.getParameterValue("account"));
            _createdDoc.getValues().put(CISales.TransactionAbstract.Account.name,
                            _parameter.getParameterValue("account"));

            transIns.execute();
        }
    }

    protected void createDocumentTax(final Parameter _parameter,
                                     final CreatedDoc _createdDoc)
        throws EFapsException
    {
        _parameter.getParameterValues("amount4DocCreate");
        _parameter.getParameterValues("option4DocCreate");

        _createdDoc.getValues().get(
                        getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.RateCurrencyLink.name));
        _createdDoc.getValues().get(
                        getFieldName4Attribute(_parameter, CISales.PaymentDocumentAbstract.CurrencyLink.name));

        for (int i = 0; i < getPaymentCount(_parameter); i++) {
//            if (option4Create != null && amount4Create != null) {
//                if (((Long) curId).equals(rateCurId) && isIncomingInvoiceValid(_parameter, _createdDoc, i)
//                            && parseBigDecimal(amount4Create[i]).compareTo(BigDecimal.ZERO) > 0) {
//                    final String valueDoc = option4Create[i];
//                    if ("IncomingRetention".equalsIgnoreCase(valueDoc)) {
//                        _createdDoc.addValue(IncomingRetention_Base.AMOUNTVALUE, parseBigDecimal(amount4Create[i]));
//                        new IncomingRetention().create4Doc(_parameter, _createdDoc, i);
//                    } else if ("IncomingDetraction".equalsIgnoreCase(valueDoc)) {
//                        _createdDoc.addValue(IncomingDetraction_Base.AMOUNTVALUE, parseBigDecimal(amount4Create[i]));
//                        new IncomingDetraction().create4Doc(_parameter, _createdDoc, i);
//                    }
//                }
//            }
        }
    }

    protected boolean isIncomingInvoiceValid(final Parameter _parameter,
                                             final CreatedDoc _createdDoc,
                                             final int _index)
    {
        boolean ret = false;
        if (!_createdDoc.getPositions().isEmpty() &&
                        CISales.IncomingInvoice.getType().equals(_createdDoc.getPositions().get(_index).getType())) {
            ret = true;
        }
        return ret;
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
     * @throws EFapsException on error
     */
    protected void add2PaymentCreate(final Parameter _parameter,
                                     final Insert _payInsert,
                                     final CreatedDoc _createdDoc,
                                     final int _idx)
          throws EFapsException
    {
        // used by implementation
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return listmap for autocomplete
     * @throws EFapsException on error
     */
    public Return autoComplete4CreateDocument(final Parameter _parameter)
        throws EFapsException
    {

        final Instance contactInst = Instance.get(_parameter.getParameterValue("contact"));
        final boolean check = !"true".equalsIgnoreCase(_parameter.getParameterValue("checkbox4Invoice"));
        final boolean showRevision = "true".equalsIgnoreCase(getProperty(_parameter, "ShowRevision"));

        final String input = (String) _parameter.get(ParameterValues.OTHERS);

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();

        final QueryBuilder queryBldr = getQueryBldrFromProperties(_parameter);
        final QueryBuilder attrQueryBldr = getQueryBldrFromProperties(_parameter);
        attrQueryBldr.addWhereAttrMatchValue(CISales.DocumentAbstract.Name, input + "*").setIgnoreCase(true);
        if (showRevision) {
            attrQueryBldr.addWhereAttrMatchValue(CISales.DocumentAbstract.Revision, input + "*").setIgnoreCase(true);
            attrQueryBldr.setOr(true);
        }
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.DocumentAbstract.ID);
        queryBldr.addWhereAttrInQuery(CISales.DocumentAbstract.ID, attrQuery);
        queryBldr.addOrderByAttributeAsc(CISales.DocumentAbstract.Date);
        queryBldr.addOrderByAttributeAsc(CISales.DocumentAbstract.Name);

        if (contactInst.isValid() && check) {
            queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.Contact, contactInst.getId());
        }
        InterfaceUtils.addMaxResult2QueryBuilder4AutoComplete(_parameter, queryBldr);

        add2QueryBldr4autoComplete4CreateDocument(_parameter, queryBldr);

        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selConName = SelectBuilder.get().linkto(CISales.DocumentAbstract.Contact)
                        .attribute(CIContacts.ContactAbstract.Name);
        multi.addSelect(selConName);
        multi.addAttribute(CISales.DocumentAbstract.Date,
                        CISales.DocumentAbstract.Name,
                        CISales.DocumentSumAbstract.RateCrossTotal,
                        CISales.DocumentAbstract.Revision);
        final SelectBuilder selCur = new SelectBuilder()
                        .linkto(CISales.DocumentSumAbstract.RateCurrencyId).instance();
        multi.addSelect(selCur);
        multi.setEnforceSorted(true);
        multi.execute();

        while (multi.next()) {
            final String conName = multi.<String>getSelect(selConName);
            final String name = multi.<String>getAttribute(CISales.DocumentAbstract.Name);
            final String revision = multi.<String>getAttribute(CISales.DocumentAbstract.Revision);
            final String oid = multi.getCurrentInstance().getOid();
            final DateTime date = multi.<DateTime>getAttribute(CISales.DocumentAbstract.Date);

            final StringBuilder choice = new StringBuilder()
                            .append(name).append(" - ").append(Instance.get(oid).getType().getLabel())
                            .append(" - ").append(date.toString(DateTimeFormat.forStyle("S-")
                                            .withLocale(Context.getThreadContext().getLocale())));
            if (multi.getCurrentInstance().getType().isKindOf(CISales.DocumentSumAbstract.getType())) {
                final BigDecimal amount = multi
                                .<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                final CurrencyInst curr = new CurrencyInst(multi.<Instance>getSelect(selCur));
                choice.append(" - ").append(curr.getSymbol()).append(" ")
                                .append(NumberFormatter.get().getTwoDigitsFormatter().format(amount));
            }
            choice.append(" - ").append(conName);
            if (showRevision) {
                choice.append(" - ").append(revision);
            }
            final Map<String, String> map = new HashMap<String, String>();
            map.put(EFapsKey.AUTOCOMPLETE_KEY.getKey(), oid);
            map.put(EFapsKey.AUTOCOMPLETE_VALUE.getKey(), name);
            map.put(EFapsKey.AUTOCOMPLETE_CHOICE.getKey(), choice.toString());
            list.add(map);
        }

        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }


    /**
     * Method to override if a default value is required for a document.
     *
     * @param _parameter as passed from eFaps API
     * @return DropDownfield
     * @throws EFapsException on error.
     */
    public Return dropDown4AccountFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final org.efaps.esjp.common.uiform.Field field = new org.efaps.esjp.common.uiform.Field()
        {
            @Override
            protected void add2QueryBuilder4List(final Parameter _parameter,
                                                 final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final Map<Integer, String> activations = analyseProperty(_parameter, "Activation");
                final List<AccountCDActivation> pactivt = new ArrayList<AccountCDActivation>();
                for (final String activation : activations.values()) {
                    final AccountCDActivation pDAct = AccountCDActivation.valueOf(activation);
                    pactivt.add(pDAct);
                }
                if (!pactivt.isEmpty()) {
                    _queryBldr.addWhereAttrEqValue(CISales.AccountCashDesk.Activation, pactivt.toArray());
                }
            };
        };
        return field.dropDownFieldValue(_parameter);
    }


    public Return updateFields4AbsoluteAmount(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final Return retVal = new Return();
        final BigDecimal amount2Pay = getAmount4Pay(_parameter).abs();
        map.put("amount", NumberFormatter.get().getTwoDigitsFormatter().format(amount2Pay));
        map.put("total4DiscountPay", NumberFormatter.get().getTwoDigitsFormatter().format(amount2Pay.subtract(getSum4Positions(_parameter, true))));
        list.add(map);
        retVal.put(ReturnValues.VALUES, list);

        if (amount2Pay.compareTo(BigDecimal.ZERO) == 0) {
            Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT);
        } else {
            Context.getThreadContext().setSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT, true);
        }

        return retVal;
    }

    protected BigDecimal getAmount4Pay(final Parameter _parameter)
        throws EFapsException
    {
        return parseBigDecimal(_parameter.getParameterValue("amount"));
    }

    /**
     * Method to round amount divide.
     *
     * @param _amount4PayDoc BigDecimal of the amount finally.
     * @param _rate BigDecimal of the division amount.
     * @return BigDecimal rounding.
     */
    protected BigDecimal getRound4Amount(final BigDecimal _amount4PayDoc,
                                       final BigDecimal _rate)
    {
        return _amount4PayDoc.divide(_rate, 2, BigDecimal.ROUND_HALF_UP);
    }

    public Return updateFields4PaymentDiscount(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final int selected = getSelectedRow(_parameter);
        final String amountStr = _parameter.getParameterValues("payment4Pay")[selected];
        final String discountStr = _parameter.getParameterValues("paymentDiscount")[selected];
        final String payAmountStr = _parameter.getParameterValues("paymentAmount")[selected];

        final BigDecimal amount = parseBigDecimal(amountStr);
        final BigDecimal payAmount = parseBigDecimal(payAmountStr);
        final BigDecimal discount = parseBigDecimal(discountStr);

        final BigDecimal discAmount = amount.multiply(discount.divide(new BigDecimal(100)))
                                                .setScale(2, BigDecimal.ROUND_HALF_UP);
        map.put("paymentAmount", NumberFormatter.get().getTwoDigitsFormatter().format(amount.subtract(discAmount)));
        map.put("paymentAmountDesc", NumberFormatter.get().getTwoDigitsFormatter().format(amount.subtract(amount.subtract(discAmount))));
        final BigDecimal recalculatePos = getSum4Positions(_parameter, true)
                                                    .subtract(payAmount).add(amount.subtract(discAmount));
        BigDecimal total4DiscountPay = BigDecimal.ZERO;
        if (Context.getThreadContext().getSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT) == null) {
            map.put("amount", NumberFormatter.get().getTwoDigitsFormatter().format(recalculatePos));
        } else {
            total4DiscountPay = getAmount4Pay(_parameter).abs().subtract(recalculatePos);
        }
        map.put("total4DiscountPay", NumberFormatter.get().getTwoDigitsFormatter().format(total4DiscountPay));
        list.add(map);

        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, list);
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
        } catch (final ParseException e) {
            ret = BigDecimal.ZERO;
        }
        return ret;
    }

    public Return updateFields4PaymentAmount(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final int selected = getSelectedRow(_parameter);
        final Instance docInstance = Instance.get(_parameter.getParameterValues("createDocument")[selected]);
        final Instance accInstance = Instance.get(CISales.AccountCashDesk.getType(),
                                                        _parameter.getParameterValue("account"));
        if (docInstance.isValid() && accInstance.isValid()) {
            final String payStr = _parameter.getParameterValues("paymentAmount")[selected];
            final String amount4PayStr = _parameter.getParameterValues("payment4Pay")[selected];

            final BigDecimal pay = parseBigDecimal(payStr);
            final BigDecimal amount4PayTotal = parseBigDecimal(amount4PayStr);

            map.put("paymentAmount", NumberFormatter.get().getTwoDigitsFormatter().format(pay));
            map.put("paymentAmountDesc", NumberFormatter.get().getTwoDigitsFormatter().format(amount4PayTotal.subtract(pay)));
            map.put("paymentDiscount", NumberFormatter.get().getTwoDigitsFormatter().format(BigDecimal.ZERO));
            BigDecimal total4DiscountPay = BigDecimal.ZERO;
            if (Context.getThreadContext().getSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT) == null) {
                map.put("amount", NumberFormatter.get().getTwoDigitsFormatter().format(getSum4Positions(_parameter, true)));
            } else {
                total4DiscountPay = getAmount4Pay(_parameter).abs().subtract(getSum4Positions(_parameter, true));
            }
            map.put("total4DiscountPay", NumberFormatter.get().getTwoDigitsFormatter().format(total4DiscountPay));
            list.add(map);
        }
        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    protected String getSymbol4Document(final String _doc,
                                        final String _linkTo,
                                        final String _attribute)
        throws EFapsException
    {
        String ret = "";
        final Instance docInst = Instance.get(_doc);
        if (docInst.isValid()) {
            final SelectBuilder selSymbol = new SelectBuilder().linkto(_linkTo).attribute(_attribute);
            final PrintQuery print = new PrintQuery(_doc);
            print.addSelect(selSymbol);
            print.execute();
            ret = print.<String>getSelect(selSymbol);
        }
        return ret;
    }

    protected BigDecimal getPayments4Document(final String _doc)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        final Instance docInst = Instance.get(_doc);
        if (docInst.isValid()) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.Payment);
            queryBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, docInst.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CISales.Payment.Amount);
            multi.execute();
            while (multi.next()) {
                ret = ret.add(multi.<BigDecimal>getAttribute(CISales.Payment.Amount));
            }
        }
        return ret;
    }

    protected BigDecimal getAttribute4Document(final String _doc,
                                               final String _attribute)
        throws EFapsException
    {
        BigDecimal ret = BigDecimal.ZERO;
        final Instance docInst = Instance.get(_doc);
        if (docInst.isValid()) {
            final PrintQuery print = new PrintQuery(_doc);
            print.addAttribute(_attribute);
            print.execute();
            ret = print.<BigDecimal>getAttribute(_attribute);
        }
        return ret == null ? BigDecimal.ZERO : ret;
    }



    public Return update4checkbox4Invoive(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String contactOid = _parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Contact.name));
        final String check = _parameter.getParameterValue("checkbox4Invoice");
        final Instance contact = Instance.get(contactOid);
        if (check == null && !"true".equalsIgnoreCase(check)) {
            if (contact.isValid()) {
                Context.getThreadContext().setSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY, contact);
            } else {
                Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);
            }
        } else {
            Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);
        }
        Context.getThreadContext().setSessionAttribute(AbstractPaymentDocument_Base.CONTACT_SESSIONKEY, contact);
        return ret;
    }

    public Return updateFields4RateCurrency(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final PrintQuery print = new PrintQuery(CISales.AccountCashDesk.getType(),
                        _parameter.getParameterValue("account"));
        print.addAttribute(CISales.AccountCashDesk.CurrencyLink);
        print.execute();

        final Instance newInst = Instance.get(CIERP.Currency.getType(),
                        print.<Long>getAttribute(CISales.AccountCashDesk.CurrencyLink));

        final Instance baseInst = Currency.getBaseCurrency();

        final Map<String, String> map = new HashMap<String, String>();
        final BigDecimal[] rates = new PriceUtil().getRates(_parameter, newInst, baseInst);
        map.put("rate", NumberFormatter.get().getFormatter(0, 3).format(rates[3]));
        map.put("rate" + RateUI.INVERTEDSUFFIX, "" + (rates[3].compareTo(rates[0]) != 0));
        list.add(map);

        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    public Return updateFields4Position(final Parameter _parameter)
        throws EFapsException
    {
        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final StringBuilder js = new StringBuilder();
        js.append(getTableRemoveScript(_parameter, getTableName(_parameter)));

        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
        list.add(map);

        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    public String getTableName(final Parameter _parameter) {
        return "paymentTable";
    }

    public Return updateFields4Contact(final Parameter _parameter)
        throws EFapsException
    {
        final Instance contact = Instance.get(_parameter.getParameterValue(getFieldName4Attribute(_parameter,
                        CISales.PaymentDocumentAbstract.Contact.name)));
        final String check = _parameter.getParameterValue("checkbox4Invoice");
        if (contact.isValid() && check == null && !"true".equalsIgnoreCase(check)) {
            Context.getThreadContext().setSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY, contact);
        } else {
            Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);
        }
        Context.getThreadContext().setSessionAttribute(AbstractPaymentDocument_Base.CONTACT_SESSIONKEY, contact);
        return new Return();
    }

    public Return deactivateFiltered4Invoice(final Parameter _parameter)
        throws EFapsException
    {
        Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);
        Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.CONTACT_SESSIONKEY);
        Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT);
        return new Return();
    }



    /**
     * Method to get a formater.
     *
     * @return a formater
     * @throws EFapsException on error
     */
    protected DecimalFormat getZeroDigitsformater()
        throws EFapsException
    {
        return NumberFormatter.get().getZeroDigitsFormatter();
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Object from UI
     * @throws EFapsException on error
     */
    protected Object[] getRateObject(final Parameter _parameter)
        throws EFapsException
    {
        return getRateObject(_parameter, "rate", 0);
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _field     rate field name
     * @param _index     inde to be used
     * @return Object from UI
     * @throws EFapsException on error
     */
    protected Object[] getRateObject(final Parameter _parameter,
                                     final String _field,
                                     final int _index)
        throws EFapsException
    {
        BigDecimal rate = BigDecimal.ONE;
        try {
            rate = (BigDecimal) RateFormatter.get().getFrmt4Rate().parse(_parameter.getParameterValues(_field)[_index]);
        } catch (final ParseException e) {
            throw new EFapsException(AbstractDocument_Base.class, "analyzeRate.ParseException", e);
        }
        final boolean rInv = "true"
                        .equalsIgnoreCase(_parameter.getParameterValues(_field + RateUI.INVERTEDSUFFIX)[_index]);
        return new Object[] { rInv ? BigDecimal.ONE : rate, rInv ? rate : BigDecimal.ONE };
    }

    protected String getCode4GeneratedDocWithSysConfig(final Parameter _parameter)
        throws EFapsException
    {
        String ret = "";
        // Sales-Configuration
        final SystemConfiguration config = Sales.getSysConfig();
        if (config != null) {
            if (getType4DocCreate(_parameter).isKindOf(CISales.PaymentDocumentAbstract.getType())) {
                final boolean active = config.getAttributeValueAsBoolean(SalesSettings.ACTIVATECODE4PAYMENTDOCUMENT);
                if (active) {
                    final String uuid = config.getAttributeValue(SalesSettings.SEQUENCE4PAYMENTDOCUMENT);
                    ret = NumberGenerator.get(UUID.fromString(uuid)).getNextVal();
                }
            } else if (getType4DocCreate(_parameter).isKindOf(CISales.PaymentDocumentOutAbstract.getType())) {
                final boolean active = config.getAttributeValueAsBoolean(SalesSettings.ACTIVATECODE4PAYMENTDOCUMENTOUT);
                if (active) {
                    final String uuid = config.getAttributeValue(SalesSettings.SEQUENCE4PAYMENTDOCUMENTOUT);
                    ret = NumberGenerator.get(UUID.fromString(uuid)).getNextVal();
                }
            }
        }
        return !ret.isEmpty() ? ret : null;
    }

    protected boolean getActive4GenerateReport(final Parameter _parameter)
        throws EFapsException
    {
        boolean ret = false;
        final SystemConfiguration config = Sales.getSysConfig();
        if (config != null) {
            final boolean active = config.getAttributeValueAsBoolean(SalesSettings.ACTIVATEPRINTREPORT4PAYMENTDOCUMENT);
            if (active) {
                ret = true;
            }
        }
        return ret;
    }

    public Return validatePaymentDocument(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        final BigDecimal amount4Doc = getAmount4Pay(_parameter);
        final BigDecimal pos4Doc = getSum4Positions(_parameter, true);
        if (amount4Doc.compareTo(pos4Doc) == 0) {
            html.append(DBProperties.getProperty("org.efaps.esjp.sales.payment.PaymentCorrect"));
        } else {
            if (amount4Doc.compareTo(pos4Doc) == 1) {
                html.append(DBProperties.getProperty("org.efaps.esjp.sales.payment.PaymentPositive"));
            } else {
                html.append(DBProperties.getProperty("org.efaps.esjp.sales.payment.PaymentNegative"));
            }
        }
        ret.put(ReturnValues.SNIPLETT, html.toString());
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed from the EFaps API.
     * @return new Return with SNIPPLET.
     * @throws EFapsException
     */
    public Return validatePaymentDocument4Positions(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();

        if (!evaluateDocument4PositionDoc(_parameter).toString().isEmpty()) {
            ret.put(ReturnValues.SNIPLETT, evaluateDocument4PositionDoc(_parameter).toString());
        } else {
            ret.put(ReturnValues.TRUE, true);
        }

        return ret;
    }

    /**
     * Method to analyze positions and check is property validate Documents.
     *
     * @param _parameter Parameter as passed from the EFaps API.
     * @return new HtmlTable.
     * @throws EFapsException on error.
     */
    protected HtmlTable evaluateDocument4PositionDoc(final Parameter _parameter)
        throws EFapsException
    {
        final HtmlTable html = new HtmlTable();
        final Map<Integer, String> map = analyseProperty(_parameter, "Document");

        final String[] paymentDocs = _parameter.getParameterValues("createDocument");
        final String[] paymentAutoDocs = _parameter.getParameterValues("createDocumentAutoComplete");
        for (int i = 0; i < getPaymentCount(_parameter); i++) {
            boolean exists = false;
            final Instance document = Instance.get(paymentDocs[i]);
            for (final Entry<Integer, String> entryMap : map.entrySet()) {
                final Type type = Type.get(entryMap.getValue());
                if (type != null && document.isValid()) {
                    if (type.equals(document.getType())) {
                        exists = true;
                        break;
                    }
                }
            }
            if (!exists) {
                html.tr()
                    .td(document.getType().getLabel())
                    .td(paymentAutoDocs[i])
                    .trC();
            }
        }

        final HtmlTable html2 = new HtmlTable();
        if (!html.toString().isEmpty()) {
            html2.table()
                .th(DBProperties.getProperty("Sales_DocumentAbstract/Type.Label"))
                .th(DBProperties.getProperty("Sales_DocumentAbstract/Name.Label"))
                .append(html.toString())
                .tableC();
        }

        return html2;
    }

    protected void connectPaymentDocument2Document(final Parameter _parameter,
                                                   final CreatedDoc _createdDoc)
        throws EFapsException
    {
        // to be implemented
    }

    public Return createReportDoc(final Parameter _parameter,
                                  final CreatedDoc _createdDoc)
        throws EFapsException
    {
        Return ret = new Return();

        if (getActive4GenerateReport(_parameter)) {
            final StandartReport report = new StandartReport();
            final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
            _parameter.put(ParameterValues.INSTANCE, _createdDoc.getInstance());
            Object name = _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.Code.name);
            if (name == null) {
                name = _createdDoc.getValues().get(CISales.PaymentDocumentAbstract.Name.name);
            }

            final String fileName = DBProperties.getProperty(_createdDoc.getInstance().getType().getName() + ".Label")
                            + (name == null ? "" : "_" + name);
            report.setFileName(fileName);
            final SelectBuilder selCurName = new SelectBuilder().linkto(CISales.AccountCashDesk.CurrencyLink)
                            .attribute(CIERP.Currency.Name);

            if (_createdDoc.getValues().containsKey("accountName")) {
                report.getJrParameters().put("accountName", _createdDoc.getValues().get("accountName"));
                report.getJrParameters().put("accountCurrencyName", _createdDoc.getValues().get("accountCurrencyName"));
            } else {
                report.getJrParameters().put("accountName",
                                getSelectString4AttributeAccount((String) _createdDoc.getValues()
                                                .get(CISales.TransactionAbstract.Account.name),
                                                null, CISales.AccountCashDesk.Name));
                report.getJrParameters().put("accountCurrencyName",
                                getSelectString4AttributeAccount((String) _createdDoc.getValues()
                                                .get(CISales.TransactionAbstract.Account.name), selCurName, null));
            }
            final SystemConfiguration config = ERP.getSysConfig();
            if (config != null) {
                final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
                final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

                if (companyName != null && companyTaxNumb != null && !companyName.isEmpty()
                                && !companyTaxNumb.isEmpty()) {
                    report.getJrParameters().put("CompanyName", companyName);
                    report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
                }
            }

            if (_parameter.getInstance().getType().isKindOf(CISales.PaymentDocumentOutAbstract.getType())) {
                report.getJrParameters().put("ClientOrSupplier", DBProperties.getProperty(
                                "org.efaps.esjp.sales.payment.AbstractDocumentOutPaymentSupplier.Label"));
            } else if (_parameter.getInstance().getType().isKindOf(CISales.PaymentDocumentAbstract.getType())) {
                report.getJrParameters().put("ClientOrSupplier", DBProperties.getProperty(
                                "org.efaps.esjp.sales.payment.AbstractDocumentInPaymentClient.Label"));
            }

            addParameter4Report(_parameter, _createdDoc, report);
            ret = report.execute(_parameter);
            ret.put(ReturnValues.TRUE, true);

            try {
                final File file = (File) ret.get(ReturnValues.VALUES);
                final InputStream input = new FileInputStream(file);
                final Checkin checkin = new Checkin(_createdDoc.getInstance());
                checkin.execute(fileName + "." + properties.get("Mime"), input, ((Long) file.length()).intValue());
            } catch (final FileNotFoundException e) {
                throw new EFapsException(Invoice.class, "create.FileNotFoundException", e);
            }
        }
        return ret;
    }

    protected String getSelectString4AttributeAccount(final String _accountId,
                                                      final SelectBuilder _select,
                                                      final CIAttribute _attribute)
        throws EFapsException
    {
        String ret = "";
        if (_accountId != null) {
            final PrintQuery print = new PrintQuery(CISales.AccountCashDesk.getType(), _accountId);
            if (_select != null) {
                print.addSelect(_select);
            } else if (_attribute != null) {
                print.addAttribute(_attribute);
            }
            print.execute();
            if (_select != null) {
                ret = print.getSelect(_select);
            } else if (_attribute != null) {
                ret = print.getAttribute(_attribute);
            }
        }
        return ret.isEmpty() ? null : ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _queryBldr queryBuilder to add to
     */
    protected void add2QueryBldr4autoComplete4CreateDocument(final Parameter _parameter,
                                                             final QueryBuilder _queryBldr)
        throws EFapsException
    {
        // used by implementation
    }

    protected void addParameter4Report(final Parameter _parameter,
                                       final CreatedDoc _createdDoc,
                                       final StandartReport _report)
        throws EFapsException
    {
        // used bt implementation
    }

    public Return update4StatusCanceled (final Parameter _parameter)
        throws EFapsException
    {
        inverseTransactions(_parameter, _parameter.getInstance(), true);
        final Update updatePayment = new Update(_parameter.getInstance());
        updatePayment.add(CISales.Payment.Amount, BigDecimal.ZERO);
        updatePayment.executeWithoutAccessCheck();

        return new StatusValue().setStatus(_parameter);
    }

    /**
     * Inverse the transactions of a PaymentDocument.
     * @param _parameter Parameter as passed by the eFaps API
     * @param _instance instance of the Payment Document
     * @param _isTargetDocument is it a targetDocument (PaymentDocument)
     * @throws EFapsException on error
     */
    protected void inverseTransactions(final Parameter _parameter,
                                       final Instance _instance,
                                       final boolean _isTargetDocument)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CISales.Payment);
        if (_isTargetDocument) {
            queryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, _instance);
        } else {
            queryBldr.addWhereAttrEqValue(CISales.Payment.CreateDocument, _instance);
        }
        final InstanceQuery queryInst = queryBldr.getQuery();
        queryInst.execute();
        while (queryInst.next()) {
            final QueryBuilder queryBldr2 = new QueryBuilder(CISales.TransactionAbstract);
            queryBldr2.addWhereAttrEqValue(CISales.TransactionAbstract.Payment, queryInst.getCurrentValue());
            final MultiPrintQuery multi = queryBldr2.getPrint();
            multi.addAttribute(CISales.TransactionAbstract.Amount,
                            CISales.TransactionAbstract.CurrencyId,
                            CISales.TransactionAbstract.Account);
            multi.execute();
            boolean updatePayment = false;
            while (multi.next()) {
                Insert insert;
                if (multi.getCurrentInstance().getType().isKindOf(CISales.TransactionOutbound.getType())) {
                    insert = new Insert(CISales.TransactionInbound);
                } else {
                    insert = new Insert(CISales.TransactionOutbound);
                }
                insert.add(CISales.TransactionAbstract.Amount,
                                multi.<BigDecimal>getAttribute(CISales.TransactionAbstract.Amount));
                insert.add(CISales.TransactionAbstract.CurrencyId,
                                multi.<Long>getAttribute(CISales.TransactionAbstract.CurrencyId));
                insert.add(CISales.TransactionAbstract.Account,
                                multi.<Long>getAttribute(CISales.TransactionAbstract.Account));
                insert.add(CISales.TransactionAbstract.Payment, queryInst.getCurrentValue().getId());
                insert.add(CISales.TransactionAbstract.Description, DBProperties.getProperty(
                                AbstractPaymentDocument.class.getName() +  ".correctionPayment"));
                insert.add(CISales.TransactionAbstract.Date, new DateTime());
                insert.execute();

                if (insert.getInstance().isValid()) {
                    updatePayment = true;
                }
            }

            if (updatePayment) {
                final Update update = new Update(queryInst.getCurrentValue());
                update.add(CISales.Payment.Amount, BigDecimal.ZERO);
                update.executeWithoutAccessCheck();
            }
        }
    }


    public Return getPayments4Document(final Parameter _parameter)
        throws EFapsException
    {
        return new MultiPrint()
        {
            @Override
            protected void add2QueryBldr(final Parameter _parameter,
                                         final QueryBuilder _queryBldr)
                throws EFapsException
            {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.PayableDocument2Document);
                attrQueryBldr.addWhereAttrEqValue(CISales.PayableDocument2Document.ToLink,
                                _parameter.getInstance().getId());
                final AttributeQuery attrQuery = attrQueryBldr
                                .getAttributeQuery(CISales.PayableDocument2Document.FromLink);

                _queryBldr.addWhereAttrInQuery(CISales.Payment.CreateDocument, attrQuery);
                _queryBldr.setOr(true);
            }
        }.execute(_parameter);
    }

    /**
     * Executed the command on the button.
     *
     * @param _parameter Parameter as passed from the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return executeButton(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        StringBuilder js = new StringBuilder();
        final String account = _parameter.getParameterValue("account");
        final PrintQuery printAccount = new PrintQuery(Instance.get(CISales.AccountCashDesk.getType(), account));
        final SelectBuilder selCurInst = new SelectBuilder().linkto(
                        CISales.AccountAbstract.CurrencyLink).instance();
        printAccount.addSelect(selCurInst);
        printAccount.execute();
        final Instance currencyId = printAccount.<Instance>getSelect(selCurInst);

        BigDecimal restAmount = getAmount4Pay(_parameter);
        BigDecimal sumPayments = BigDecimal.ZERO;
        final List<Instance> instances = getDocInstances(_parameter);
        final List<Instance> instances2Print = new ArrayList<Instance>();

        for (final Instance inst : instances) {
            final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentAbstract);
            queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.ID, inst.getId());
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CISales.DocumentAbstract.Name, CISales.DocumentSumAbstract.RateCrossTotal);
            final SelectBuilder selCurrencyInst = new SelectBuilder()
                            .linkto(CISales.DocumentSumAbstract.RateCurrencyId).instance();
            multi.addSelect(selCurrencyInst);
            multi.execute();
            if (multi.next()) {
                final Instance instCurrency = multi.<Instance>getSelect(selCurrencyInst);

                final BigDecimal pay = BigDecimal.ZERO;
                final String doc = inst.getOid();
                final BigDecimal total4Doc = getAttribute4Document(doc, CISales.DocumentSumAbstract.RateCrossTotal.name);
                final BigDecimal payments4Doc = getPayments4Document(doc);
                final BigDecimal amount2Pay = total4Doc.subtract(payments4Doc);
                BigDecimal amountDue = amount2Pay.subtract(pay);

                if (!currencyId.equals(instCurrency)) {
                    final Instance baseInstDoc = Instance.get(CIERP.Currency.getType(), currencyId.getId());
                    final BigDecimal[] rates = new PriceUtil().getRates(_parameter, baseInstDoc, instCurrency);
                    amountDue = amountDue.multiply(rates[2]);
                }

                instances2Print.add(multi.getCurrentInstance());
                if (amountDue.compareTo(restAmount) == 1 || amountDue.compareTo(restAmount) == 0) {
                    sumPayments = sumPayments.add(restAmount);
                    break;
                } else {
                    restAmount = restAmount.subtract(amountDue);
                    sumPayments = sumPayments.add(amountDue);
                }

            }
        }

        js = buildHtml4ExecuteButton(_parameter, instances2Print, restAmount, sumPayments, currencyId);
        ret.put(ReturnValues.SNIPLETT, js.toString());
        return ret;
    }

    protected StringBuilder buildHtml4ExecuteButton(final Parameter _parameter,
                                                    final List<Instance> _instancesList,
                                                    final BigDecimal _restAmount,
                                                    final BigDecimal _sumPayments,
                                                    final Instance _currencyActual)
        throws EFapsException
    {
        final StringBuilder js = new StringBuilder();
        js.append(getTableRemoveScript(_parameter, "paymentTable"));

        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();

        int index = 0;
        boolean lastPos = false;
        for (final Instance payment : _instancesList) {
            final Map<String, Object> map = new HashMap<String, Object>();
            values.add(map);
            if (_instancesList.size() == index + 1) {
                lastPos = true;
            }
            add2MapPositions(_parameter, payment, index, lastPos, _restAmount, _currencyActual, map);
            index++;
        }

        js.append("\n");
        js.append(getTableAddNewRowsScript(_parameter, "paymentTable", values, null));

        final BigDecimal total4DiscountPay = getAmount4Pay(_parameter).abs().subtract(_sumPayments);
        js.append(getSetFieldValue(0, CIFormSales.Sales_PaymentCheckWithOutDocForm.total4DiscountPay.name,
                        total4DiscountPay == null ? BigDecimal.ZERO.toString() : NumberFormatter.get().getTwoDigitsFormatter().format(total4DiscountPay)));

        return js;
    }

    /**
     * @param _instance Instance of each document
     * @param _index index of each document
     * @param _lastPosition boolean to indicate the last position to print
     * @param _restAmount last quantity to pay
     * @param _currencyActual current currency of document
     * @return StringBuilder
     * @throws EFapsException on error
     */
    protected void add2MapPositions(final Parameter _parameter,
                                          final Instance _instance,
                                          final Integer _index,
                                          final boolean _lastPosition,
                                          final BigDecimal _restAmount,
                                    final Instance _currencyActual,
                                    final Map<String, Object> _map)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CISales.DocumentAbstract);
        queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.ID, _instance.getId());
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CISales.DocumentAbstract.Name, CISales.DocumentSumAbstract.RateCrossTotal, CISales.DocumentSumAbstract.CurrencyId);
        final SelectBuilder selCurrencyInst = new SelectBuilder()
                        .linkto(CISales.DocumentSumAbstract.RateCurrencyId).instance();
        multi.addSelect(selCurrencyInst);
        multi.execute();
        if (multi.next()){
            final String name = multi.<String>getAttribute(CISales.DocumentAbstract.Name);
            final BigDecimal rateCrossTotal = multi.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);

            BigDecimal pay = BigDecimal.ZERO;
            final String doc = _instance.getOid();
            final BigDecimal total4Doc = getAttribute4Document(doc, CISales.DocumentSumAbstract.RateCrossTotal.name);
            final BigDecimal payments4Doc = getPayments4Document(doc);
            final BigDecimal amount2Pay = total4Doc.subtract(payments4Doc);
            final BigDecimal amountDue = amount2Pay.subtract(pay);
            final String symbol = getSymbol4Document(_instance.getOid(), CISales.DocumentSumAbstract.RateCurrencyId.name, CIERP.Currency.Symbol.name);
            final StringBuilder bldr = new StringBuilder();
            bldr.append(NumberFormatter.get().getTwoDigitsFormatter().format(rateCrossTotal)).append(" / ")
                .append(NumberFormatter.get().getTwoDigitsFormatter().format(payments4Doc)).append(" - ").append(symbol);

            _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.createDocument.name, _instance.getOid());
            _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.createDocument.name + "AutoComplete", name);
            _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.createDocumentDesc.name, bldr.toString());

            final Instance currencyDocInst = multi.<Instance>getSelect(selCurrencyInst);

            BigDecimal amountDueConverted = amountDue;
            BigDecimal restAmountConverted = _restAmount;
            if (!_currencyActual.equals(currencyDocInst)) {
                final Instance baseInstDoc = Instance.get(CIERP.Currency.getType(), _currencyActual.getId());
                final BigDecimal[] rates = new PriceUtil().getRates(_parameter, baseInstDoc, currencyDocInst);
                amountDueConverted = amountDueConverted.multiply(rates[2]);
                restAmountConverted = restAmountConverted.multiply(rates[3]);
            }

            if (!_lastPosition) {
                _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.paymentAmount.name, amountDue == null
                                ? BigDecimal.ZERO.toString() : NumberFormatter.get().getTwoDigitsFormatter().format(amountDueConverted));
                pay = amountDue;
            } else {
                if (amount2Pay.compareTo(pay) != 0) {
                    _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.paymentAmount.name,
                                    _restAmount == null
                                                    ? BigDecimal.ZERO.toString() : NumberFormatter.get().getTwoDigitsFormatter().format(
                                                                    amountDueConverted));
                    pay = amountDue;
                } else {
                    _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.paymentAmount.name,
                                    _restAmount == null
                                                    ? BigDecimal.ZERO.toString() : NumberFormatter.get().getTwoDigitsFormatter().format(
                                                                    _restAmount));
                    pay = restAmountConverted;
                }
            }

            _map.put(CITableSales.Sales_PaymentCheckWithOutDocPaymentTable.paymentAmountDesc.name,
                            NumberFormatter.get().getTwoDigitsFormatter().format(amount2Pay.subtract(pay)));
        }

    }

    protected List<Instance> getDocInstances(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> instances = new ArrayList<Instance>();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final Instance contactInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);

        final Instance contactSessionInst = (Instance) Context.getThreadContext().getSessionAttribute(
                        AbstractPaymentDocument_Base.CONTACT_SESSIONKEY);
        for (int i = 0; i < 100; i++) {
            if (props.containsKey("Type" + i)) {
                final Type type = Type.get(String.valueOf(props.get("Type" + i)));
                if (type != null) {
                    final QueryBuilder queryBldr = new QueryBuilder(type);
                    if (contactInst != null && contactInst.isValid() && contactSessionInst != null
                                    && contactSessionInst.isValid()) {
                        queryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.Contact, contactInst.getId());
                        queryBldr.addOrderByAttributeAsc(CISales.DocumentAbstract.Date);
                        queryBldr.addOrderByAttributeAsc(CISales.DocumentAbstract.Name);
                    }

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
                    final InstanceQuery query = queryBldr.getQuery();
                    query.execute();
                    instances.addAll(query.getValues());
                }
            }
        }

        final List<Long> listIds = new ArrayList<Long>();
        for (final Instance instanceAux : instances) {
            listIds.add(instanceAux.getId());
        }
        if (!listIds.isEmpty()) {
            final QueryBuilder queryBldrDocs = new QueryBuilder(CISales.DocumentAbstract);
            queryBldrDocs.addWhereAttrEqValue(CISales.DocumentAbstract.ID, listIds.toArray());
            queryBldrDocs.addOrderByAttributeAsc(CISales.DocumentAbstract.Date);
            queryBldrDocs.addOrderByAttributeAsc(CISales.DocumentAbstract.Name);
            final MultiPrintQuery multi = queryBldrDocs.getPrint();
            multi.setEnforceSorted(true);
            multi.execute();
            instances.clear();
            instances.addAll(multi.getInstanceList());
        }

        return instances;
    }

    protected List<Map<String, Object>> convertMap4Script(final Parameter _parameter,
                                                          final Collection<Map<KeyDef, Object>> _values)
        throws EFapsException
    {
        final List<Map<String, Object>> ret = new ArrayList<Map<String, Object>>();
        for (final Map<KeyDef, Object> valueMap : _values) {
            final Map<String, Object> map = new HashMap<String, Object>();
            for (final Entry<KeyDef, Object> entry : valueMap.entrySet()) {
                map.put(entry.getKey().getName(), entry.getKey().convert4Map(entry.getValue()));
            }
            ret.add(map);
        }
        return ret;
    }


    /**
     * Method to update fields with document selected.
     *
     * @param _parameter Parameter from eFaps API.
     * @return return with values.
     * @throws EFapsException on error.
     */
    public Return updateFields4DocumentSelected(final Parameter _parameter)
        throws EFapsException
    {
        Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY);
        Context.getThreadContext().removeSessionAttribute(AbstractPaymentDocument_Base.CONTACT_SESSIONKEY);

        final Return ret = new Return();

        final Instance selectDoc = Instance.get(_parameter.getParameterValue("name"));

        final List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        final Map<String, Object> map = new HashMap<String, Object>();
        if (selectDoc.isValid()) {
            final SelectBuilder selContact = new SelectBuilder().linkto(CISales.DocumentAbstract.Contact);
            final SelectBuilder selContactOid = new SelectBuilder(selContact).oid();
            final SelectBuilder selContactName = new SelectBuilder(selContact).attribute(CIContacts.Contact.Name);

            final Map<Integer, String> fields = analyseProperty(_parameter, "Fields");
            if (!fields.isEmpty()) {
                final PrintQuery print = new PrintQuery(selectDoc);
                print.addAttribute(CISales.DocumentSumAbstract.Date,
                                CISales.DocumentSumAbstract.DueDate,
                                CISales.DocumentSumAbstract.RateCrossTotal);
                print.addSelect(selContactOid, selContactName);
                print.execute();

                for (final Entry<Integer, String> field : fields.entrySet()) {
                    String value;
                    String value2 = null;
                    if (field.getValue().equalsIgnoreCase("amount")) {
                        final BigDecimal amount =
                                        print.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.RateCrossTotal);
                        if (amount.compareTo(BigDecimal.ZERO) == 0) {
                            Context.getThreadContext()
                                            .removeSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT);
                        } else {
                            Context.getThreadContext()
                                            .setSessionAttribute(AbstractPaymentDocument_Base.CHANGE_AMOUNT, true);
                        }
                        value = NumberFormatter.get().getTwoDigitsFormatter().format(amount);
                    } else if (field.getValue().equalsIgnoreCase("contact")) {
                        value = print.<String>getSelect(selContactOid);
                        value2 = print.<String>getSelect(selContactName);
                    } else if (field.getValue().equalsIgnoreCase("date_eFapsDate")) {
                        final DateTime date = print.<DateTime>getAttribute(CISales.DocumentSumAbstract.Date);
                        value = date.toString("dd/MM/YY");
                    } else if (field.getValue().equalsIgnoreCase("dueDate_eFapsDate")) {
                        final DateTime dueDate = print.<DateTime>getAttribute(CISales.DocumentSumAbstract.DueDate);
                        value = dueDate.toString("dd/MM/YY");
                    } else {
                        value = "";
                    }
                    if (value != null && !value.isEmpty()) {
                        if (value2 == null) {
                            map.put(field.getValue(), StringEscapeUtils.escapeEcmaScript(value));
                        } else {
                            map.put(field.getValue(), new String[] { StringEscapeUtils.escapeEcmaScript(value),
                                            StringEscapeUtils.escapeEcmaScript(value2) });
                        }
                    }
                }
                if (map.containsKey("contact")) {
                    final Instance contactInst = Instance.get(((String[]) map.get("contact"))[0]);
                    if (contactInst.isValid()) {
                        Context.getThreadContext()
                                        .setSessionAttribute(AbstractPaymentDocument_Base.INVOICE_SESSIONKEY,
                                                        contactInst);
                        Context.getThreadContext()
                                        .setSessionAttribute(AbstractPaymentDocument_Base.CONTACT_SESSIONKEY,
                                                        contactInst);
                    }
                }
                list.add(map);
            }
        }
        ret.put(ReturnValues.VALUES, list);
        return ret;
    }

    public DocPaymentInfo getNewDocPaymentInfo(final Parameter _parameter,
                                               final Instance _instance)
        throws EFapsException
    {
        return new DocPaymentInfo(_instance).setParameter(_parameter);
    }

    /**
     * @param _parameter    Parameter as passed by the eFasp API
     * @param _payDocInst instance of a PaymentDocument
     * @return StringBuilder
     * @throws EFapsException on error
     */
    public static StringBuilder getTransactionHtml(final Parameter _parameter,
                                                   final Instance _payDocInst)
        throws EFapsException
    {
        final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Payment);
        attrQueryBldr.addWhereAttrEqValue(CISales.Payment.TargetDocument, _payDocInst);
        final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(CISales.Payment.ID);

        final QueryBuilder queryBldr = new QueryBuilder(CISales.TransactionAbstract);
        queryBldr.addWhereAttrInQuery(CISales.TransactionAbstract.Payment, attrQuery);
        final MultiPrintQuery multi = queryBldr.getPrint();
        final SelectBuilder selCurInst = SelectBuilder.get().linkto(CISales.TransactionAbstract.CurrencyId).instance();
        final SelectBuilder selAccName = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account)
                        .attribute(CISales.AccountAbstract.Name);
        final SelectBuilder selAccDescr = SelectBuilder.get().linkto(CISales.TransactionAbstract.Account)
                        .attribute(CISales.AccountAbstract.Description);
        multi.addSelect(selCurInst, selAccName, selAccDescr);
        multi.addAttribute(CISales.TransactionAbstract.Amount, CISales.TransactionAbstract.Description);
        multi.execute();
        final HtmlTable html = new HtmlTable();
        html.table();
        while (multi.next()) {
            final CurrencyInst currencyInst = new CurrencyInst(multi.<Instance>getSelect(selCurInst));
            html.tr()
                .td(multi.getCurrentInstance().getType().getLabel())
                .td(NumberFormatter.get().getTwoDigitsFormatter().format(
                            multi.getAttribute(CISales.TransactionAbstract.Amount)))
                .td(currencyInst.getISOCode())
                .td(multi.<String>getSelect(selAccName))
                .td(multi.<String>getSelect(selAccDescr))
                .trC();
        }
        html.tableC();
        return new StringBuilder(html.toString());
    }
}
