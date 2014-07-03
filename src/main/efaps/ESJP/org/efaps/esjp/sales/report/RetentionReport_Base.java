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


package org.efaps.esjp.sales.report;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.DynamicReports;
import net.sf.dynamicreports.report.builder.column.ComponentColumnBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.GenericElementBuilder;
import net.sf.dynamicreports.report.builder.expression.AbstractComplexExpression;
import net.sf.dynamicreports.report.builder.group.ColumnGroupBuilder;
import net.sf.dynamicreports.report.builder.subtotal.AggregationSubtotalBuilder;
import net.sf.dynamicreports.report.definition.ReportParameters;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import org.apache.commons.collections4.comparators.ComparatorChain;
import org.efaps.admin.datamodel.Status;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIContacts;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport;
import org.efaps.esjp.common.jasperreport.AbstractDynamicReport_Base;
import org.efaps.esjp.common.jasperreport.datatype.DateTimeDate;
import org.efaps.esjp.common.jasperreport.datatype.DateTimeMonth;
import org.efaps.esjp.erp.FilteredReport;
import org.efaps.esjp.sales.util.Sales;
import org.efaps.esjp.sales.util.SalesSettings;
import org.efaps.ui.wicket.models.EmbeddedLink;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Report used to analyze retention for documents
 * by grouping them by month and contact.
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("7a7daab0-5142-4131-a782-81f59b68b68e")
@EFapsRevision("$Rev$")
public abstract class RetentionReport_Base
    extends FilteredReport
{

    /**
     * Logging instance used in this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(RetentionReport.class);

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
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String mime = (String) props.get("Mime");
        final AbstractDynamicReport dyRp = getReport(_parameter);
        dyRp.setFileName(DBProperties.getProperty(RetentionReport.class.getName() + ".FileName"));
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
     * @param _parameter Parameter as passed by the eFasp API
     * @return the report class
     * @throws EFapsException on error
     */
    protected AbstractDynamicReport getReport(final Parameter _parameter)
        throws EFapsException
    {
        return new DynRetentionReport(this);
    }

    /**
     * Report class.
     */
    public static class DynRetentionReport
        extends AbstractDynamicReport
    {

        /**
         * variable to report.
         */
        private final RetentionReport_Base filteredReport;

        /**
         * @param _report class used
         */
        public DynRetentionReport(final RetentionReport_Base _report)
        {
            this.filteredReport = _report;
        }

        @Override
        protected JRDataSource createDataSource(final Parameter _parameter)
            throws EFapsException
        {
            final List<DocumentBean> datasource = new ArrayList<>();
            final Map<Instance, DocumentBean> inst2bean = new HashMap<>();
            final QueryBuilder queryBldr = getQueryBldrFromProperties(_parameter);
            add2QueryBldr(_parameter, queryBldr);

            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selContactName = SelectBuilder.get().linkto(CISales.DocumentAbstract.Contact)
                            .attribute(CIContacts.ContactAbstract.Name);
            multi.addSelect(selContactName);
            multi.addAttribute(CISales.DocumentAbstract.Name, CISales.DocumentAbstract.Revision,
                            CISales.DocumentAbstract.Date, CISales.DocumentSumAbstract.CrossTotal,
                            CISales.DocumentAbstract.StatusAbstract);
            multi.execute();
            while (multi.next()) {
                final DocumentBean bean = getBean(_parameter);
                datasource.add(bean);
                inst2bean.put(multi.getCurrentInstance(), bean);
                bean.setOid(multi.getCurrentInstance().getOid());
                bean.setName(multi.<String>getAttribute(CISales.DocumentAbstract.Name));
                bean.setTypeLabel(multi.getCurrentInstance().getType().getLabel());
                bean.setContactName(multi.<String>getSelect(selContactName));
                bean.setDate(multi.<DateTime>getAttribute(CISales.DocumentAbstract.Date));
                bean.setCrossTotal(multi.<BigDecimal>getAttribute(CISales.DocumentSumAbstract.CrossTotal));
                bean.setRevision(multi.<String>getAttribute(CISales.DocumentAbstract.Revision));
                bean.setStatusLabel(Status.get(multi.<Long>getAttribute(CISales.DocumentAbstract.StatusAbstract))
                                .getLabel());
            }

            analyzePayments(_parameter, inst2bean);

            final ComparatorChain<DocumentBean> chain = new ComparatorChain<DocumentBean>();
            chain.addComparator(new Comparator<DocumentBean>() {
                    @Override
                    public int compare(final DocumentBean _o1,
                                       final DocumentBean _o2)
                    {
                        return Integer.valueOf(_o1.getDate().getYear())
                                        .compareTo(Integer.valueOf(_o2.getDate().getYear()));
                    }
                }
            );
            chain.addComparator(new Comparator<DocumentBean>() {
                    @Override
                    public int compare(final DocumentBean _o1,
                                       final DocumentBean _o2)
                    {
                        return Integer.valueOf(_o1.getDate().getMonthOfYear())
                                        .compareTo(Integer.valueOf(_o2.getDate().getMonthOfYear()));
                    }
                }
            );
            chain.addComparator(new Comparator<DocumentBean>() {
                    @Override
                    public int compare(final DocumentBean _o1,
                                       final DocumentBean _o2)
                    {
                        return _o1.getContactName().compareTo(_o2.getContactName());
                    }
                }
            );
            chain.addComparator(new Comparator<DocumentBean>() {
                    @Override
                    public int compare(final DocumentBean _o1,
                                       final DocumentBean _o2)
                    {
                        return _o1.getDate().compareTo(_o2.getDate());
                    }
                }
            );
            Collections.sort(datasource, chain);

            return new JRBeanCollectionDataSource(datasource);
        }


        /**
         * @param _parameter Parameter as passed by the eFaps API
         * @param _inst2value mapping
         * @throws EFapsException on error
         */
        protected void analyzePayments(final Parameter _parameter,
                                       final Map<Instance, DocumentBean> _inst2value)
            throws EFapsException
        {
            final QueryBuilder attrQueryBldr = getQueryBldrFromProperties(_parameter);
            add2QueryBldr(_parameter, attrQueryBldr);
            final QueryBuilder queryBldr = new QueryBuilder(CISales.Payment);
            queryBldr.addWhereAttrInQuery(CISales.Payment.CreateDocument,
                            attrQueryBldr.getAttributeQuery(CISales.DocumentAbstract.ID));
            final MultiPrintQuery multi = queryBldr.getPrint();
            final SelectBuilder selDocInst = SelectBuilder.get().linkto(CISales.Payment.CreateDocument).instance();
            final SelectBuilder selPayDocInst = SelectBuilder.get().linkto(CISales.Payment.TargetDocument).instance();
            multi.addSelect(selDocInst, selPayDocInst);
            multi.addAttribute(CISales.Payment.Amount, CISales.Payment.Rate);
            multi.execute();
            while (multi.next()) {
                final Instance docInst = multi.<Instance>getSelect(selDocInst);
                final Instance payDocInst = multi.<Instance>getSelect(selPayDocInst);
                final DocumentBean docBean = _inst2value.get(docInst);
                final BigDecimal amount = multi.getAttribute(CISales.Payment.Amount);

//                final Object[] rateObj = multi.getAttribute(CISales.Payment.Rate);
//                final BigDecimal rate = ((BigDecimal) rateObj[0]).divide((BigDecimal) rateObj[1], 12,
//                                BigDecimal.ROUND_HALF_UP);
//                amount = amount.multiply(rate).setScale(2, BigDecimal.ROUND_HALF_UP);

                if (payDocInst.getType().isKindOf(CISales.PaymentRetentionOut.getType())) {
                    docBean.addRetention(amount);
                } else {
                    docBean.addPayment(amount);
                }
            }
        }


        /**
         * @param _parameter  Parameter as passed by the eFaps API
         * @param _queryBldr    QueryBuilder to add to
         * @throws EFapsException on error
         */
        protected void add2QueryBldr(final Parameter _parameter,
                                     final QueryBuilder _queryBldr)
            throws EFapsException
        {
            final Map<String, Object> filter = this.filteredReport.getFilterMap(_parameter);
            if (filter.containsKey("contact") && filter.get("contact") instanceof ContactFilterValue) {
                final Instance contactInst = ((ContactFilterValue) filter.get("contact")).getObject();
                if (contactInst.isValid()) {
                    _queryBldr.addWhereAttrEqValue(CIERP.DocumentAbstract.Contact, contactInst);
                }
            }
            if (filter.containsKey("dateFrom")) {
                final DateTime date = (DateTime) filter.get("dateFrom");
                _queryBldr.addWhereAttrGreaterValue(CIERP.DocumentAbstract.Date,
                                date.withTimeAtStartOfDay().minusSeconds(1));
            }
            if (filter.containsKey("dateTo")) {
                final DateTime date = (DateTime) filter.get("dateTo");
                _queryBldr.addWhereAttrLessValue(CIERP.DocumentAbstract.Date,
                                date.withTimeAtStartOfDay().plusDays(1));
            }

            final Instance docTypeInst = Sales.getSysConfig().getLink(SalesSettings.PROFSERVDOCTYP);
            if (docTypeInst != null && docTypeInst.isValid()) {
                final QueryBuilder attrQueryBldr = new QueryBuilder(CISales.Document2DocumentType);
                attrQueryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentTypeLink, docTypeInst);
                _queryBldr.addWhereAttrNotInQuery(CIERP.DocumentAbstract.ID,
                                attrQueryBldr.getAttributeQuery(CISales.Document2DocumentType.DocumentLink));
            } else {
                RetentionReport_Base.LOG.error("Missing or invalid SystemConfiguration for report: {}",
                                SalesSettings.PROFSERVDOCTYP);
            }
        }

        @Override
        protected void addColumnDefintion(final Parameter _parameter,
                                          final JasperReportBuilder _builder)
            throws EFapsException
        {
            final TextColumnBuilder<String> nameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Name"), "name",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<String> revisionColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Revision"), "revision",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<String> typeColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Type"), "typeLabel",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<String> contactNameColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.ContactName"), "contactName",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<DateTime> dateColumn = AbstractDynamicReport_Base.column(
                            DBProperties.getProperty(RetentionReport.class.getName() + ".Column.Date"), "date",
                            DateTimeDate.get());
            final TextColumnBuilder<DateTime> monthColumn = AbstractDynamicReport_Base.column(
                            DBProperties.getProperty(RetentionReport.class.getName() + ".Column.Month"), "date",
                            DateTimeMonth.get());
            final TextColumnBuilder<BigDecimal> crossTotalColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.CrossTotal"), "crossTotal",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<String> statusColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Status"), "statusLabel",
                            DynamicReports.type.stringType());
            final TextColumnBuilder<BigDecimal> paymentColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Payment"), "payment",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> retentionColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Retention"), "retention",
                            DynamicReports.type.bigDecimalType());
            final TextColumnBuilder<BigDecimal> percentColumn = DynamicReports.col.column(DBProperties
                            .getProperty(RetentionReport.class.getName() + ".Column.Percent"), "percent",
                            DynamicReports.type.bigDecimalType());

            final GenericElementBuilder linkElement = DynamicReports.cmp.genericElement(
                            "http://www.efaps.org", "efapslink")
                            .addParameter(EmbeddedLink.JASPER_PARAMETERKEY, new LinkExpression())
                            .setHeight(12).setWidth(25);
            final ComponentColumnBuilder linkColumn = DynamicReports.col.componentColumn(linkElement).setTitle("");
            if (getExType().equals(ExportType.HTML)) {
                _builder.addColumn(linkColumn);
            }

            final ColumnGroupBuilder contactGroup = DynamicReports.grp.group(contactNameColumn).groupByDataType();
            final ColumnGroupBuilder monthGroup = DynamicReports.grp.group(monthColumn).groupByDataType();

            _builder.addGroup(monthGroup, contactGroup);
            final AggregationSubtotalBuilder<BigDecimal> crossTotalSum = DynamicReports.sbt.sum(crossTotalColumn);
            final AggregationSubtotalBuilder<BigDecimal> paymentSum = DynamicReports.sbt.sum(paymentColumn);
            final AggregationSubtotalBuilder<BigDecimal> retentionSum = DynamicReports.sbt.sum(retentionColumn);

            _builder.addColumn(monthColumn, contactNameColumn, typeColumn, revisionColumn, nameColumn,
                            dateColumn, crossTotalColumn, paymentColumn, retentionColumn, percentColumn, statusColumn);
            _builder.addSubtotalAtGroupFooter(contactGroup, crossTotalSum);
            _builder.addSubtotalAtGroupFooter(contactGroup, paymentSum);
            _builder.addSubtotalAtGroupFooter(contactGroup, retentionSum);
        }

        /**
         * @param _parameter Parameter as passed by the eFaps API
         * @return new DocumentBean
         * @throws EFapsException on error
         */
        protected DocumentBean getBean(final Parameter _parameter)
            throws EFapsException
        {
            return new DocumentBean();
        }

        /**
         * Getter method for the instance variable {@link #filteredReport}.
         *
         * @return value of instance variable {@link #filteredReport}
         */
        protected RetentionReport_Base getFilteredReport()
        {
            return this.filteredReport;
        }
    }


    /**
     * Expression used to render a link for the UserInterface.
     */
    public static class LinkExpression
        extends AbstractComplexExpression<EmbeddedLink>
    {

        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Costructor.
         */
        public LinkExpression()
        {
            addExpression(DynamicReports.field("oid", String.class));
        }

        @Override
        public EmbeddedLink evaluate(final List<?> _values,
                                     final ReportParameters _reportParameters)
        {
            final String oid = (String) _values.get(0);
            return EmbeddedLink.getJasperLink(oid);
        }
    }


    /**
     * DataBean.
     */
    public static class DocumentBean
    {
        /**
         * OID of the document.
         */
        private String oid;

        /**
         * Name.
         */
        private String name;

        /**
         * Label of the type.
         */
        private String typeLabel;

        /**
         * Name of the contact.
         */
        private String contactName;

        /**
         * Date.
         */
        private DateTime date;

        /**
         * Normal payment.
         */
        private BigDecimal payment = BigDecimal.ZERO;

        /**
         * CrossTotal.
         */
        private BigDecimal retention = BigDecimal.ZERO;

        /**
         * CrossTotal.
         */
        private BigDecimal crossTotal;

        /**
         * Revision.
         */
        private String revision;

        /**
         * Status label.
         */
        private String statusLabel;

        /**
         * Getter method for the instance variable {@link #name}.
         *
         * @return value of instance variable {@link #name}
         */
        public String getName()
        {
            return this.name;
        }

        /**
         * @param _amount
         */
        public void addPayment(final BigDecimal _amount)
        {
            this.payment = this.payment.add(_amount);
        }

        /**
         * @param _amount
         */
        public void addRetention(final BigDecimal _amount)
        {
            this.retention = this.retention.add(_amount);
        }

        /**
         * Setter method for instance variable {@link #name}.
         *
         * @param _name value for instance variable {@link #name}
         */
        public void setName(final String _name)
        {
            this.name = _name;
        }

        /**
         * Getter method for the instance variable {@link #typeLabel}.
         *
         * @return value of instance variable {@link #typeLabel}
         */
        public String getTypeLabel()
        {
            return this.typeLabel;
        }

        /**
         * Setter method for instance variable {@link #typeLabel}.
         *
         * @param _typeLabel value for instance variable {@link #typeLabel}
         */
        public void setTypeLabel(final String _typeLabel)
        {
            this.typeLabel = _typeLabel;
        }

        /**
         * Getter method for the instance variable {@link #contactName}.
         *
         * @return value of instance variable {@link #contactName}
         */
        public String getContactName()
        {
            return this.contactName;
        }

        /**
         * Setter method for instance variable {@link #contactName}.
         *
         * @param _contactName value for instance variable {@link #contactName}
         */
        public void setContactName(final String _contactName)
        {
            this.contactName = _contactName;
        }


        /**
         * Getter method for the instance variable {@link #date}.
         *
         * @return value of instance variable {@link #date}
         */
        public DateTime getDate()
        {
            return this.date;
        }


        /**
         * Setter method for instance variable {@link #date}.
         *
         * @param _date value for instance variable {@link #date}
         */
        public void setDate(final DateTime _date)
        {
            this.date = _date;
        }


        /**
         * Getter method for the instance variable {@link #crossTotal}.
         *
         * @return value of instance variable {@link #crossTotal}
         */
        public BigDecimal getCrossTotal()
        {
            return this.crossTotal;
        }


        /**
         * Setter method for instance variable {@link #crossTotal}.
         *
         * @param _crossTotal value for instance variable {@link #crossTotal}
         */
        public void setCrossTotal(final BigDecimal _crossTotal)
        {
            this.crossTotal = _crossTotal;
        }


        /**
         * Getter method for the instance variable {@link #revision}.
         *
         * @return value of instance variable {@link #revision}
         */
        public String getRevision()
        {
            return this.revision;
        }


        /**
         * Setter method for instance variable {@link #revision}.
         *
         * @param _revision value for instance variable {@link #revision}
         */
        public void setRevision(final String _revision)
        {
            this.revision = _revision;
        }


        /**
         * Getter method for the instance variable {@link #statusLabel}.
         *
         * @return value of instance variable {@link #statusLabel}
         */
        public String getStatusLabel()
        {
            return this.statusLabel;
        }


        /**
         * Setter method for instance variable {@link #statusLabel}.
         *
         * @param _statusLabel value for instance variable {@link #statusLabel}
         */
        public void setStatusLabel(final String _statusLabel)
        {
            this.statusLabel = _statusLabel;
        }


        /**
         * Getter method for the instance variable {@link #oid}.
         *
         * @return value of instance variable {@link #oid}
         */
        public String getOid()
        {
            return this.oid;
        }


        /**
         * Setter method for instance variable {@link #oid}.
         *
         * @param _oid value for instance variable {@link #oid}
         */
        public void setOid(final String _oid)
        {
            this.oid = _oid;
        }


        /**
         * Getter method for the instance variable {@link #payment}.
         *
         * @return value of instance variable {@link #payment}
         */
        public BigDecimal getPayment()
        {
            return this.payment;
        }


        /**
         * Setter method for instance variable {@link #payment}.
         *
         * @param _payment value for instance variable {@link #payment}
         */
        public void setPayment(final BigDecimal _payment)
        {
            this.payment = _payment;
        }


        /**
         * Getter method for the instance variable {@link #retention}.
         *
         * @return value of instance variable {@link #retention}
         */
        public BigDecimal getRetention()
        {
            return this.retention;
        }


        /**
         * Setter method for instance variable {@link #retention}.
         *
         * @param _retention value for instance variable {@link #retention}
         */
        public void setRetention(final BigDecimal _retention)
        {
            this.retention = _retention;
        }


        /**
         * Getter method for the instance variable {@link #percent}.
         *
         * @return value of instance variable {@link #percent}
         */
        public BigDecimal getPercent()
        {
            BigDecimal ret;
            if (getCrossTotal().compareTo(BigDecimal.ZERO) != 0) {
                ret = new BigDecimal(100).setScale(8).divide(getCrossTotal(), BigDecimal.ROUND_HALF_UP)
                                .multiply(getRetention()).setScale(2, BigDecimal.ROUND_HALF_UP);
            } else {
                ret = BigDecimal.ZERO;
            }
            return ret;
        }
    }
}