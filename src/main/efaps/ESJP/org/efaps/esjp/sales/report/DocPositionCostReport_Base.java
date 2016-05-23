/*
 * Copyright 2003 - 2016 The eFaps Team
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

package org.efaps.esjp.sales.report;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.esjp.erp.AbstractGroupedByDate;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.erp.CurrencyInst;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.products.Cost;
import org.efaps.esjp.products.reports.CostReport;
import org.efaps.esjp.products.reports.CostReport_Base.CostTypeFilterValue;
import org.efaps.esjp.sales.report.DocPositionGroupedByDate_Base.ValueList;
import org.efaps.esjp.sales.report.DocPositionReport_Base.ContactGroup;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabColumnGroupBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabMeasureBuilder;
import net.sf.dynamicreports.report.builder.crosstab.CrosstabRowGroupBuilder;
import net.sf.dynamicreports.report.constant.Calculation;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

/**
 *
 * @author The eFaps Team
 */
@EFapsUUID("4a3b5f5d-9122-47fa-95fb-a5af567324d0")
@EFapsApplication("eFapsApp-Sales")
public abstract class DocPositionCostReport_Base
    extends FilteredReport
{

    /**
     * Values for the report.
     */
    private ValueList valueList;

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing html snipplet
     * @throws EFapsException on error
     */
    public Return generateReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final AbstractDynamicReport dyRp = getReport(_parameter);
        final String html = dyRp.getHtmlSnipplet(_parameter);
        ret.put(ReturnValues.SNIPLETT, html);
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return Return containing the file
     * @throws EFapsException on error
     */
    public Return exportReport(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final String mime = getProperty(_parameter, "Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(getDBProperty("FileName"));
        File file = null;
        if ("xls".equalsIgnoreCase(mime)) {
            file = dyRp.getExcel(_parameter);
        } else if ("pdf".equalsIgnoreCase(mime)) {
            file = dyRp.getPDF(_parameter);
        }
        ret.put(ReturnValues.VALUES, file);
        ret.put(ReturnValues.TRUE, true);
        return ret;
    }

    /**
     * Gets the cost type field value.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the cost type field value
     * @throws EFapsException on error
     */
    public Return getCostTypeFieldValue(final Parameter _parameter)
        throws EFapsException
    {
        final CostReport costReport = new CostReport();
        return costReport.getCostTypeFieldValue(_parameter);
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynDocPositionCostReport(this);
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return list of DataBeans
     * @throws EFapsException on error
     */
    protected ValueList getData(final Parameter _parameter)
        throws EFapsException
    {
        if (this.valueList == null) {
            final DocPositionGroupedByDate ds = new DocPositionGroupedByDate()
            {
                @Override
                protected void add2QueryBuilder(final Parameter _parameter,
                                                final QueryBuilder _queryBldr)
                    throws EFapsException
                {
                    super.add2QueryBuilder(_parameter, _queryBldr);
                    DocPositionCostReport_Base.this.add2QueryBuilder(_parameter, _queryBldr);
                }
            };
            final Map<String, Object> filterMap = getFilterMap(_parameter);
            final DateTime start;
            final DateTime end;
            if (filterMap.containsKey("dateFrom")) {
                start = (DateTime) filterMap.get("dateFrom");
            } else {
                start = new DateTime();
            }
            if (filterMap.containsKey("dateTo")) {
                end = (DateTime) filterMap.get("dateTo");
            } else {
                end = new DateTime();
            }
            final List<Type> typeList;
            if (filterMap.containsKey("type")) {
                typeList = new ArrayList<>();
                final TypeFilterValue filters = (TypeFilterValue) filterMap.get("type");
                for (final Long typeid : filters.getObject()) {
                    typeList.add(Type.get(typeid));
                }
            } else {
                typeList = getTypeList(_parameter);
            }
            final Properties props = getProperties4TypeList(_parameter);
            final AbstractGroupedByDate.DateGroup dateGroup;
            if (filterMap.containsKey("dateGroup") && filterMap.get("dateGroup") != null) {
                dateGroup = (AbstractGroupedByDate.DateGroup) ((EnumFilterValue) filterMap.get("dateGroup"))
                                .getObject();
            } else {
                dateGroup = DocumentSumGroupedByDate_Base.DateGroup.MONTH;
            }

            this.valueList = ds.getValueList(_parameter, start, end, dateGroup, props,
                            typeList.toArray(new Type[typeList.size()]));

            final ContactGroup contactGroup = evaluateContactGroup(_parameter);
            if (ContactGroup.PRODPRODUCER.equals(contactGroup)
                            || ContactGroup.PRODSUPPLIER.equals(contactGroup)) {
                for (final Map<String, Object> value  : this.valueList) {
                    final Instance prodInst = (Instance) value.get("productInst");
                    final CachedPrintQuery print = CachedPrintQuery.get4Request(prodInst);
                    final SelectBuilder selContactName = ContactGroup.PRODPRODUCER.equals(contactGroup)
                                    ? SelectBuilder.get().linkfrom(CIProducts.Product2Producer.From).linkto(
                                            CIProducts.Product2Producer.To).attribute(CIContacts.ContactAbstract.Name)
                                    : SelectBuilder.get().linkfrom(CIProducts.Product2Supplier.From).linkto(
                                            CIProducts.Product2Supplier.To).attribute(CIContacts.ContactAbstract.Name);
                    print.addSelect(selContactName);
                    print.executeWithoutAccessCheck();
                    value.put("contact", print.getSelect(selContactName));
                }
            }

            Instance selected = null;
            if (filterMap.containsKey("currency")) {
                final CurrencyFilterValue filter = (CurrencyFilterValue) filterMap.get("currency");
                if (filter.getObject() instanceof Instance) {
                    if (filter.getObject().isValid()) {
                        selected = filter.getObject();
                    } else {
                        selected = Currency.getBaseCurrency();
                    }
                }
            }

            final CostTypeFilterValue filterValue = (CostTypeFilterValue) filterMap.get("costType");
            Instance alterInst = null;
            if (filterValue != null) {
                alterInst = Instance.get(filterValue.getObject());
            }
            for (final Map<String, Object> value : this.valueList) {
                final DateTime docDate = (DateTime) value.get("docDate");
                final Instance prodInst = (Instance) value.get("productInst");
                final BigDecimal quantity = (BigDecimal) value.get("quantity");
                final BigDecimal cost;
                if (InstanceUtils.isValid(alterInst)) {
                    cost = Cost.getAlternativeCost4Currency(_parameter, alterInst, prodInst, selected);
                } else {
                    cost = Cost.getCost4Currency(_parameter, docDate, prodInst, selected);
                }
                value.put("cost", cost.multiply(quantity));
            }
        }
        return this.valueList;
    }

    @Override
    protected Properties getProperties4TypeList(final Parameter _parameter)
        throws EFapsException
    {
        Properties ret = null;
        if (containsProperty(_parameter, "SystemConfig")) {
            final String sysConfstr = getProperty(_parameter, "SystemConfig");
            final SystemConfiguration config;
            if (isUUID(sysConfstr)) {
                config = SystemConfiguration.get(UUID.fromString(sysConfstr));
            } else {
                config = SystemConfiguration.get(sysConfstr);
            }
            if (config != null) {
                ret = config.getAttributeValueAsProperties(getProperty(_parameter, "ConfigAttribute"), true);
            }
        }
        if (ret == null) {
            ret = Sales.DOCPOSCOSTREPORT.get();
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @param _queryBldr queryBuilder to add to
     * @throws EFapsException on error
     */
    protected void add2QueryBuilder(final Parameter _parameter,
                                    final QueryBuilder _queryBldr)
        throws EFapsException
    {
        final Map<String, Object> filterMap = getFilterMap(_parameter);
        // if sums are shown and only one currency should be shown filter the positions by it
        if (_queryBldr.getType().isCIType(CISales.PositionSumAbstract) &&  filterMap.containsKey("currency")) {
            final CurrencyFilterValue filter = (CurrencyFilterValue) filterMap.get("currency");
            if (filter.getObject() instanceof Instance && filter.getObject().isValid()) {
                _queryBldr.addWhereAttrEqValue(CISales.PositionSumAbstract.RateCurrencyId, filter.getObject());
            }
        }
        if (filterMap.containsKey("contact")) {
            final InstanceFilterValue filterValue = (InstanceFilterValue) filterMap.get("contact");
            if (filterValue != null && filterValue.getObject().isValid()) {
                final ContactGroup contactGroup = evaluateContactGroup(_parameter);
                switch (contactGroup) {
                    case PRODPRODUCER:
                        final QueryBuilder p2pAttrQueryBldr = new QueryBuilder(CIProducts.Product2Producer);
                        p2pAttrQueryBldr.addWhereAttrEqValue(CIProducts.Product2Producer.To, filterValue.getObject());

                        _queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.Product,
                                        p2pAttrQueryBldr.getAttributeQuery(CIProducts.Product2Producer.From));
                        break;
                    case PRODSUPPLIER:
                        final QueryBuilder p2sAttrQueryBldr = new QueryBuilder(CIProducts.Product2Supplier);
                        p2sAttrQueryBldr.addWhereAttrEqValue(CIProducts.Product2Supplier.To, filterValue.getObject());

                        _queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.Product,
                                        p2sAttrQueryBldr.getAttributeQuery(CIProducts.Product2Supplier.From));
                        break;
                    default:
                        final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.DocumentAbstract);
                        attrQueryBldr.addWhereAttrEqValue(CISales.DocumentAbstract.Contact, filterValue.getObject());

                        _queryBldr.addWhereAttrInQuery(CISales.PositionAbstract.DocumentAbstractLink,
                                        attrQueryBldr.getAttributeQuery(CISales.DocumentAbstract.ID));
                        break;
                }
            }
        }
    }

    /**
     * Evaluate contact group.
     *
     * @param _parameter Parameter as passed by the eFaps API
     * @return the date config
     * @throws EFapsException on error
     */
    protected ContactGroup evaluateContactGroup(final Parameter _parameter)
        throws EFapsException
    {
        final ContactGroup ret;
        final Map<String, Object> filter = getFilterMap(_parameter);
        final EnumFilterValue value = (EnumFilterValue) filter.get("contactGroup");
        if (value != null) {
            ret = (ContactGroup) value.getObject();
        } else {
            ret = ContactGroup.NONE;
        }
        return ret;
    }

    /**
     * Dynamic Report.
     */
    public static class DynDocPositionCostReport
        extends AbstractDynamicReport
    {

        /**
         * Filtered Report.
         */
        private final DocPositionCostReport_Base filteredReport;

        /**
         * @param _filteredReport report
         */
        public DynDocPositionCostReport(final DocPositionCostReport_Base _filteredReport)
        {
            this.filteredReport = _filteredReport;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final JRRewindableDataSource ret;
            if (getFilteredReport().isCached(_parameter)) {
                ret = getFilteredReport().getDataSourceFromCache(_parameter);
                try {
                    ret.moveFirst();
                } catch (final JRException e) {
                    LOG.error("Catched JRException", e);
                }
            } else {
                final ValueList values = getFilteredReport().getData(_parameter);
                final ComparatorChain<Map<String, Object>> chain = new ComparatorChain<>();

                final ContactGroup contactGrp = this.filteredReport.evaluateContactGroup(_parameter);
                if (!ContactGroup.NONE.equals(contactGrp)) {
                    chain.addComparator(new Comparator<Map<String, Object>>()
                    {

                        @Override
                        public int compare(final Map<String, Object> _o1,
                                           final Map<String, Object> _o2)
                        {
                            return String.valueOf(_o1.get("contact")).compareTo(String.valueOf(_o2.get("contact")));
                        }
                    });
                }
                chain.addComparator(new Comparator<Map<String, Object>>()
                {

                    @Override
                    public int compare(final Map<String, Object> _o1,
                                       final Map<String, Object> _o2)
                    {
                        return String.valueOf(_o1.get("productName")).compareTo(String.valueOf(_o2.get("productName")));
                    }
                });
                chain.addComparator(new Comparator<Map<String, Object>>()
                {

                    @Override
                    public int compare(final Map<String, Object> _o1,
                                       final Map<String, Object> _o2)
                    {
                        return String.valueOf(_o1.get("partial")).compareTo(String.valueOf(_o2.get("partial")));
                    }
                });

                Collections.sort(values, chain);

                // it must be ensured that the first product has information for
                // all used partial so it is garantized that the columns are in order
                final Collection finalValues = new ArrayList<>();
                final Set<String> partials = new TreeSet<>();
                for (final Map<String, Object> value : values) {
                    partials.add((String) value.get("partial"));
                }
                if (partials.size() > 1) {
                    final Iterator<String> partialIter = partials.iterator();
                    final String productName = (String) values.get(0).get("productName");
                    final Iterator<Map<String, Object>> iter = values.iterator();
                    Map<String, Object> previous = values.get(0);
                    while (iter.hasNext()) {
                        final Map<String, Object> map = iter.next();
                        // if not al partials added go on validating
                        if (partialIter.hasNext()) {
                            String partial = partialIter.next();
                            final boolean current = productName.equals(map.get("productName"));
                            // stil the same product
                            if (current) {
                                // there is a partial missing add on for it
                                while (partial != null && partial.compareTo((String) map.get("partial")) < 0) {
                                    final Map<String, Object> newMap = new HashMap<>();
                                    for (final Entry<String, Object> entry : map.entrySet()) {
                                        if (entry.getValue() instanceof Number) {
                                            newMap.put(entry.getKey(), null);
                                        } else {
                                            newMap.put(entry.getKey(), entry.getValue());
                                        }
                                    }
                                    newMap.put("partial", partial);
                                    finalValues.add(newMap);
                                    partial = partialIter.hasNext() ? partialIter.next() : null;
                                }
                            } else {
                                final Map<String, Object> newMap = new HashMap<>();
                                for (final Entry<String, Object> entry : previous.entrySet()) {
                                    if (entry.getValue() instanceof Number) {
                                        newMap.put(entry.getKey(), null);
                                    } else {
                                        newMap.put(entry.getKey(), entry.getValue());
                                    }
                                }
                                newMap.put("partial", partial);
                                finalValues.add(newMap);
                                // perhaps there are still more values
                                while (partialIter.hasNext()) {
                                    partial = partialIter.next();
                                    final Map<String, Object> newMap1 = new HashMap<>();
                                    for (final Entry<String, Object> entry : previous.entrySet()) {
                                        if (entry.getValue() instanceof Number) {
                                            newMap1.put(entry.getKey(), null);
                                        } else {
                                            newMap1.put(entry.getKey(), entry.getValue());
                                        }
                                    }
                                    newMap1.put("partial", partial);
                                    finalValues.add(newMap1);
                                }
                            }
                            previous = map;
                        }
                        finalValues.add(map);
                    }
                } else {
                    finalValues.addAll(values);
                }
                ret = new JRMapCollectionDataSource(finalValues);
                getFilteredReport().cache(_parameter, ret);
            }
            return ret;
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final Map<String, Object> filterMap = getFilteredReport().getFilterMap(_parameter);
            final List<Type> typeList;
            if (filterMap.containsKey("type")) {
                typeList = new ArrayList<>();
                final TypeFilterValue filters = (TypeFilterValue) filterMap.get("type");
                for (final Long typeid : filters.getObject()) {
                    typeList.add(Type.get(typeid));
                }
            } else {
                typeList = getFilteredReport().getTypeList(_parameter);
            }
            boolean showAmount = true;
            for (final Type type : typeList) {
                if (!type.isKindOf(CISales.DocumentSumAbstract)) {
                    showAmount = false;
                }
            }

            final List<CrosstabRowGroupBuilder<?>> rowGrpBldrs = new ArrayList<>();
            final List<CrosstabColumnGroupBuilder<?>> colGrpBldrs = new ArrayList<>();
            final List<CrosstabMeasureBuilder<?>> measureGrpBldrs = new ArrayList<>();

            boolean base = false;
            CurrencyInst selected = null;
            if (filterMap.containsKey("currency")) {
                final CurrencyFilterValue filter = (CurrencyFilterValue) filterMap.get("currency");
                if (filter.getObject() instanceof Instance) {
                    if (filter.getObject().isValid()) {
                        selected = CurrencyInst.get(filter.getObject());
                    } else {
                        base = "BASE".equals(filter.getObject().getKey());
                    }
                }
            }
            final ContactGroup contactGrp = this.filteredReport.evaluateContactGroup(_parameter);
            if (!ContactGroup.NONE.equals(contactGrp)) {
                final CrosstabRowGroupBuilder<String> contactGroup = DynamicReports.ctab.rowGroup("contact",
                                String.class).setHeaderWidth(150);
                rowGrpBldrs.add(contactGroup);
            }

            final CrosstabMeasureBuilder<BigDecimal> quantityMeasure = DynamicReports.ctab.measure(
                            DBProperties.getProperty(DocPositionCostReport.class.getName() + ".quantity"),
                            "quantity", BigDecimal.class, Calculation.SUM);
            measureGrpBldrs.add(quantityMeasure);

            final CrosstabMeasureBuilder<BigDecimal> costMeasure = DynamicReports.ctab.measure(
                            DBProperties.getProperty(DocPositionCostReport.class.getName() + ".cost") + " "
                                            + (base || selected == null
                                                ? CurrencyInst.get(Currency.getBaseCurrency()).getSymbol()
                                                : selected.getSymbol()),
                            "cost", BigDecimal.class, Calculation.SUM);
            measureGrpBldrs.add(costMeasure);

            if (showAmount) {
                if (selected == null) {
                    if (!base) {
                        for (final CurrencyInst currency : CurrencyInst.getAvailable()) {
                            final CrosstabMeasureBuilder<BigDecimal> amountMeasure = DynamicReports.ctab.measure(
                                        currency.getSymbol(),
                                        currency.getISOCode(), BigDecimal.class, Calculation.SUM);
                            measureGrpBldrs.add(amountMeasure);
                        }
                    }
                    final CrosstabMeasureBuilder<BigDecimal> amountMeasure = DynamicReports.ctab.measure(
                                    DBProperties.getProperty(DocPositionCostReport.class.getName() + ".BASE")
                                                    + " " + CurrencyInst.get(Currency.getBaseCurrency()).getSymbol(),
                                    "BASE", BigDecimal.class, Calculation.SUM);
                    measureGrpBldrs.add(amountMeasure);
                } else {
                    final CrosstabMeasureBuilder<BigDecimal> amountMeasure = DynamicReports.ctab.measure(
                                    selected.getSymbol(),
                                    selected.getISOCode(), BigDecimal.class, Calculation.SUM);
                    measureGrpBldrs.add(amountMeasure);
                }
            }
            final CrosstabRowGroupBuilder<String> rowColGroup = DynamicReports.ctab
                            .rowGroup("productName", String.class)
                            .setShowTotal(true)
                            .setHeaderWidth(150);
            rowGrpBldrs.add(rowColGroup);

            final CrosstabRowGroupBuilder<String> rowTypeGroup = DynamicReports.ctab
                            .rowGroup("productDescr", String.class)
                            .setHeaderWidth(250)
                            .setHeaderHorizontalTextAlignment(HorizontalTextAlignment.LEFT)
                            .setShowTotal(false);
            rowGrpBldrs.add(rowTypeGroup);

            final CrosstabRowGroupBuilder<String> rowTypeGroup2 = DynamicReports.ctab.rowGroup("uoMStr", String.class)
                            .setHeaderWidth(50)
                            .setShowTotal(false);
            rowGrpBldrs.add(rowTypeGroup2);

            final CrosstabColumnGroupBuilder<String> columnGroup = DynamicReports.ctab.columnGroup("partial",
                            String.class).setShowTotal(true);
            colGrpBldrs.add(columnGroup);

            final CrosstabBuilder crosstab = DynamicReports.ctab.crosstab();
            crosstab.setCellWidth(200).setDataPreSorted(true);

            updateBldrs(rowGrpBldrs, colGrpBldrs, measureGrpBldrs);

            for (final CrosstabRowGroupBuilder<?> rowGrpBldr : rowGrpBldrs) {
                crosstab.addRowGroup(rowGrpBldr);
            }
            for (final CrosstabColumnGroupBuilder<?> colGrpBldr : colGrpBldrs) {
                crosstab.addColumnGroup(colGrpBldr);
            }
            for (final CrosstabMeasureBuilder<?> measureGrpBldr : measureGrpBldrs) {
                crosstab.addMeasure(measureGrpBldr);
            }
            _builder.addSummary(crosstab);
        }

        /**
         * Update builders.
         *
         * @param _rowGrpBldrs the row group builders
         * @param _colGrpBldrs the column group builders
         * @param _measureGrpBldrs the measure group builders
         */
        protected void updateBldrs(final List<CrosstabRowGroupBuilder<?>> _rowGrpBldrs,
                                   final List<CrosstabColumnGroupBuilder<?>> _colGrpBldrs,
                                   final List<CrosstabMeasureBuilder<?>> _measureGrpBldrs)
        {
            // to be used by implementation
        }

        /**
         * Getter method for the instance variable {@link #filteredReport}.
         *
         * @return value of instance variable {@link #filteredReport}
         */
        public DocPositionCostReport_Base getFilteredReport()
        {
            return this.filteredReport;
        }
    }
}