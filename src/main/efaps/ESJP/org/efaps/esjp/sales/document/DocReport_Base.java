/*
 * Copyright 2003 - 2010 The eFaps Team
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
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.datamodel.ui.FieldValue;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.admin.ui.AbstractUserInterfaceObject.TargetMode;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.EFapsDataSource;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.CurrencyInst_Base;
import org.efaps.esjp.erp.Rate;
import org.efaps.esjp.sales.PriceUtil;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.DateTimeUtil;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("8e6fa637-760a-48ab-8bb9-bf643f4d65d1")
@EFapsRevision("$Rev$")
public abstract class DocReport_Base
    extends EFapsDataSource
{

    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        final String dateFromStr = Context.getThreadContext().getParameter("dateFrom");
        final String dateToStr = Context.getThreadContext().getParameter("dateTo");
        final DateTime from = DateTimeUtil.normalize(new DateTime(dateFromStr));
        final DateTime to = DateTimeUtil.normalize(new DateTime(dateToStr));

        final List<Instance> instances = getInstances(_parameter, from, to);
        if (instances.size() > 0) {
            final MultiPrintQuery multiPrint = new MultiPrintQuery(instances);
            setPrint(multiPrint);
            if (_jasperReport.getMainDataset().getFields() != null) {
                for (final JRField field : _jasperReport.getMainDataset().getFields()) {
                    final String select = field.getPropertiesMap().getProperty("Select");
                    if (select != null) {
                        multiPrint.addSelect(select);
                    }
                }
            }
            multiPrint.setEnforceSorted(true);
            multiPrint.execute();
        }
    }


    /**
     * Method for obtains a new List with instance of the documents.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _from Datetime from.
     * @param _to Datetime to.
     * @return ret with list instance.
     * @throws EFapsException on error.
     */
    protected List<Instance> getInstances(final Parameter _parameter,
                                          final DateTime _from,
                                          final DateTime _to)
        throws EFapsException
    {
        final List<Instance> ret = new ArrayList<Instance>();
        final Map<String, Map<String, Instance>> values = new TreeMap<String, Map<String, Instance>>();

        values.put("A", getInstances(_parameter, CISales.Invoice.uuid, _from, _to));
        values.put("B", getInstances(_parameter, CISales.Receipt.uuid, _from, _to));
        values.put("C", getInstances(_parameter, CISales.CreditNote.uuid, _from, _to));
        values.put("D", getInstances(_parameter, CISales.Reminder.uuid, _from, _to));

        for (final Map<String, Instance> value : values.values()) {
            for (final Instance inst : value.values()) {
                ret.add(inst);
            }
        }
        return ret;
    }

    /**
     * Method for obtains instances of the documents.
     *
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _typeUUID UUID of type.
     * @param _from Datetime from.
     * @param _to Datetime to.
     * @return ret with values.
     * @throws EFapsException on error.
     */
    protected Map<String, Instance> getInstances(final Parameter _parameter,
                                                  final UUID _typeUUID,
                                                  final DateTime _from,
                                                  final DateTime _to)
        throws EFapsException
    {
        final String contactOid = _parameter.getParameterValue("contact");
        final String contactName = _parameter.getParameterValue("contactAutoComplete");

        final Map<String, Instance> ret = new TreeMap<String, Instance>();
        final QueryBuilder queryBldr = new QueryBuilder(_typeUUID);
        queryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date, _from.minusMinutes(1));
        queryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date, _to.plusDays(1));

        if (contactOid != null && !contactOid.isEmpty() && contactName != null && !contactName.isEmpty()) {
            queryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Contact, Instance.get(contactOid).getId());
        }

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIERP.DocumentAbstract.Name);
        multi.execute();
        while (multi.next()) {
            ret.put(multi.<String>getAttribute(CIERP.DocumentAbstract.Name), multi.getCurrentInstance());
        }
        return ret;
    }

    /**
     * CReate the Document Report.
     * @param _parameter    Parameter as passed from the eFaps API
     * @return report
     * @throws EFapsException on error
     */
    @SuppressWarnings("unchecked")
    public Return createDocReport(final Parameter _parameter)
        throws EFapsException
    {
        final String dateFrom = _parameter.getParameterValue("dateFrom");
        final String dateTo = _parameter.getParameterValue("dateTo");
        final String mime = _parameter.getParameterValue("mime");
        final String currency = _parameter.getParameterValue("currency");
        final Long rateCurType = Long.parseLong(_parameter.getParameterValue("rateCurrencyType"));
        final boolean active = Boolean.parseBoolean(_parameter.getParameterValue("filterActive"));
        //final String rateStr = _parameter.getParameterValue("rate");

        final CurrencyInst curInst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), currency));
        final Map<String, Object> props = (Map<String, Object>) _parameter.get(ParameterValues.PROPERTIES);
        props.put("Mime", mime);
        final DateTime from = new DateTime(dateFrom);
        final DateTime to = new DateTime(dateTo);
        final StandartReport report = new StandartReport();
        report.setFileName(getReportName(_parameter, from, to));
        report.getJrParameters().put("FromDate", from.toDate());
        report.getJrParameters().put("ToDate", to.toDate());
        report.getJrParameters().put("Mime", mime);
        report.getJrParameters().put("Currency", curInst.getName());
        report.getJrParameters().put("CurrencyId", curInst.getInstance().getId());

        addAdditionalParameters(_parameter, report);

        if (active) {
            final Map<String, BigDecimal> map = getRates4DateRange(curInst.getInstance(), from, to, rateCurType);
            report.getJrParameters().put("Rates", map);
            report.getJrParameters().put("Active", active);
            /*report.getJrParameters().put("Rate", curInst.isInvert()
                                ? BigDecimal.ONE.divide(rate, 12, BigDecimal.ROUND_HALF_UP) : rate);*/
        } else {
            report.getJrParameters().put("Active", active);
        }
        return report.execute(_parameter);
    }

    protected void addAdditionalParameters(final Parameter _parameter,
                                           final StandartReport report)
        throws EFapsException
    {
        // TODO Auto-generated method stub
    }


    protected Map<String, BigDecimal> getRates4DateRange(final Instance _curInst,
                                                            final DateTime _from,
                                                            final DateTime _to,
                                                            final Long _rateCurType)
        throws EFapsException
    {
        final Format formatter = new SimpleDateFormat("yyyyMMdd");
        final Map<String, BigDecimal> map = new HashMap<String, BigDecimal>();
        DateTime fromAux = _from;
        Rate rate;
        while (fromAux.isBefore(_to)) {
            final QueryBuilder queryBldr = new QueryBuilder(Type.get(_rateCurType));
            queryBldr.addWhereAttrEqValue(CIERP.CurrencyRateAbstract.CurrencyLink, _curInst.getId());
            queryBldr.addWhereAttrGreaterValue(CIERP.CurrencyRateAbstract.ValidUntil, fromAux.minusMinutes(1));
            queryBldr.addWhereAttrLessValue(CIERP.CurrencyRateAbstract.ValidFrom, fromAux.plusMinutes(1));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder valSel = new SelectBuilder()
                        .attribute(CIERP.CurrencyRateAbstract.Rate).value();
            final SelectBuilder labSel = new SelectBuilder()
                        .attribute(CIERP.CurrencyRateAbstract.Rate).label();
            final SelectBuilder curSel = new SelectBuilder()
                        .linkto(CIERP.CurrencyRateAbstract.CurrencyLink).oid();
            multi.addSelect(valSel, labSel, curSel);
            multi.execute();
            if (multi.next()) {
                rate = new Rate(new CurrencyInst(Instance.get(multi.<String>getSelect(curSel))),
                           multi.<BigDecimal>getSelect(valSel),
                           multi.<BigDecimal>getSelect(labSel));
            } else {
                rate = new Rate(new CurrencyInst(Instance.get(CIERP.Currency.getType(),
                                _curInst.getId())), BigDecimal.ONE);
            }

            map.put(formatter.format(fromAux.toDate()), rate.getValue());
            fromAux = fromAux.plusDays(1);
        }

        return map;
    }

    /**
     * Get the name for the report.
     *
     * @param _parameter Parameter as passed form the eFaps API
     * @param _from fromdate
     * @param _to   to date
     * @return name of the report
     */
    protected String getReportName(final Parameter _parameter,
                                   final DateTime _from,
                                   final DateTime _to)
    {
        return DBProperties.getProperty("Sales_DocReport.Label", "es")
            + "-" + _from.toString(DateTimeFormat.shortDate())
            + "-" + _to.toString(DateTimeFormat.shortDate());
    }

    /**
     * Called from the field with the rate for a document. Returning a
     * input with one rate selected.
     *
     * @param _parameter Parameter as passed by the eFaps API for ESJP
     * @return a input with one rate for currency
     * @throws EFapsException on error
     */
    public Return getRateFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final FieldValue fieldValue = (FieldValue) _parameter.get(ParameterValues.UIOBJECT);
        final StringBuilder html = new StringBuilder();
        // Sales-Configuration
        final Instance baseInst = SystemConfiguration.get(UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f"))
                        .getLink("CurrencyBase");
        final String symbol = new CurrencyInst_Base(baseInst).getSymbol();
        if (fieldValue.getTargetMode().equals(TargetMode.CREATE)) {
            if (fieldValue.getField().getName().equals("rate")) {
                html.append("<input type='text' value='1' name=\"").append(fieldValue.getField().getName())
                    .append("\" /> ").append("<span id='convert'>").append(symbol).append(" -> ").append(symbol)
                    .append("</span>");
            }
        }
        final Return retVal = new Return();
        retVal.put(ReturnValues.SNIPLETT, html.toString());
        return retVal;
    }

    /**
     * Called from the field with the rate for a document. Returning a
     * input with one rate selected.
     *
     * @param _parameter Parameter as passed by the eFaps API for ESJP
     * @return a input with one rate for currency
     * @throws EFapsException on error
     */
    public Return updateRateFieldValueUI(final Parameter _parameter)
        throws EFapsException
    {
        final String datecurr = _parameter.getParameterValue("dateCurrency_eFapsDate");
        final String curr = _parameter.getParameterValue("currency");

        final List<Map<String, String>> list = new ArrayList<Map<String, String>>();

        final Instance newInst = Instance.get(Type.get(CIERP.Currency.uuid), curr);
        final Map<String, String> map = new HashMap<String, String>();
        Instance currentInst = (Instance) Context.getThreadContext().getSessionAttribute(
                                                            AbstractDocument_Base.CURRENCYINST_KEY);
        // Sales-Configuration
        final Instance baseInst = SystemConfiguration.get(UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f"))
                        .getLink("CurrencyBase");
        if (currentInst == null) {
            currentInst = baseInst;
        }

        final StringBuilder js = new StringBuilder();
        final CurrencyInst baseCurrInst = new CurrencyInst(baseInst);
        final CurrencyInst currInst = new CurrencyInst(Instance.get(CIERP.Currency.getType(), curr));

        js.append("document.getElementById('convert').innerHTML='").append(baseCurrInst.getSymbol()).append(" -> ")
            .append(currInst.getSymbol()).append("'");
        map.put(EFapsKey.FIELDUPDATE_JAVASCRIPT.getKey(), js.toString());
        if (!newInst.equals(currentInst)) {
            final BigDecimal[] rates = new PriceUtil()
                        .getRates(DateUtil.getDateFromParameter(datecurr), newInst, currentInst);
            map.put("rate", rates[3].toString());
            list.add(map);
        } else {
            final BigDecimal[] rates = new PriceUtil()
                        .getRates(DateUtil.getDateFromParameter(datecurr), currentInst, newInst);
            map.put("rate", rates[3].toString());
            list.add(map);
        }

        final Return retVal = new Return();
        retVal.put(ReturnValues.VALUES, list);
        return retVal;
    }

    public Return updateFilterActiveUIValue(final Parameter _parameter) {
        return new Return();
    }
}
