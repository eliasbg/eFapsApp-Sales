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

package org.efaps.esjp.sales.report;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JasperReport;

import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Dimension;
import org.efaps.admin.datamodel.Dimension.UoM;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.dbproperty.DBProperties;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.AttributeQuery;
import org.efaps.db.CachedPrintQuery;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.db.SelectBuilder;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.ci.CISales;
import org.efaps.esjp.common.jasperreport.EFapsMapDataSource;
import org.efaps.esjp.common.jasperreport.StandartReport;
import org.efaps.esjp.erp.util.ERP;
import org.efaps.esjp.erp.util.ERPSettings;
import org.efaps.esjp.products.Cost;
import org.efaps.esjp.sales.Costing_Base.CostDoc;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 */
@EFapsUUID("172d6272-0b49-4c2e-a004-24d90c719a98")
@EFapsApplication("eFapsApp-Sales")
public abstract class SalesKardexReport_Base
    extends EFapsMapDataSource
{

    /**
     * Logger for this class.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(SalesKardexReport_Base.class);

    /**
     * Mapping for No es defined by SUNAT.
     */
    protected static final Map<Long, String> DOCTYPE_MAP = new HashMap<Long, String>();
    static {
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.RecievingTicket.getType().getId(), "09");
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.DeliveryNote.getType().getId(), "09");
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.ReturnSlip.getType().getId(), "09");
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.UsageReport.getType().getId(), "00");
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.ReturnUsageReport.getType().getId(), "00");
        SalesKardexReport_Base.DOCTYPE_MAP.put(CISales.TransactionDocument.getType().getId(), "00");
    }

    /**
     * Mapping for No es defined by SUNAT.
     */
    protected static final Map<Long, String> EXISTTYPE_MAP = new HashMap<Long, String>();
    static {
        SalesKardexReport_Base.EXISTTYPE_MAP.put(CIProducts.ProductMaterial.getType().getId(),
                        "03 MATERIAS PRIMAS Y AUXILIARES - MATERIALES");
    }

    /**
     * Enum used to define the keys for the map.
     */
    public enum Field
    {
        /** */
        TRANS_DATE("transDate"),
        /** */
        TRANS_DOC_TYPE("transDocType"),
        /** */
        TRANS_DOC_SERIE("transDocSerie"),
        /** */
        TRANS_DOC_NUMBER("transDocNumber"),
        /** */
        TRANS_DOC_OPERATION("transDocOperation"),
        /** */
        TRANS_INBOUND_QUANTITY("transInboundQuantity"),
        /** */
        TRANS_INBOUND_COST("transInboundCost"),
        /** */
        TRANS_OUTBOUND_QUANTITY("transOutboundQuantity");

        /**
         * key.
         */
        private final String key;

        /**
         * @param _key key
         */
        private Field(final String _key)
        {
            this.key = _key;
        }

        /**
         * Getter method for the instance variable {@link #key}.
         *
         * @return value of instance variable {@link #key}
         */
        public String getKey()
        {
            return this.key;
        }
    }

    /**
     * @param _jasperReport new JasperReport.
     * @param _parameter Parameter as passed from the eFaps API.
     * @param _parentSource parent source.
     * @param _jrParameters jasper parameters.
     * @param _prodInsts Instances of the products.
     * @throws EFapsException on error
     */
    protected void init(final JasperReport _jasperReport,
                        final Parameter _parameter,
                        final JRDataSource _parentSource,
                        final Map<String, Object> _jrParameters,
                        final Instance... _prodInsts)
        throws EFapsException
    {
        final List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
        final DateTime dateFrom = new DateTime(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.dateFrom.name));

        final DateTime dateTo = new DateTime(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.dateTo.name));

        final Instance currencyInst = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.currency.name));

        final List<Instance> listStorage = getStorageInstList(_parameter);
        boolean first = true;
        for (final Instance prodInst : _prodInsts) {
            if (first) {
                first = false;
            } else {
                values.add(new HashMap<String, Object>());
            }

            values.add(getInventoryValue(_parameter, prodInst, listStorage, dateFrom, currencyInst));
            final SelectBuilder selTransDoc = new SelectBuilder().linkto(CIProducts.TransactionInOutAbstract.Document);
            final SelectBuilder selTransDocInst = new SelectBuilder(selTransDoc).instance();
            final SelectBuilder selTransDocName = new SelectBuilder(selTransDoc).attribute(CIERP.DocumentAbstract.Name);

            final QueryBuilder queryBldr = new QueryBuilder(CIProducts.TransactionInOutAbstract);
            queryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Product, prodInst);
            queryBldr.addWhereAttrLessValue(CIProducts.TransactionInOutAbstract.Date, dateTo.plusSeconds(1));
            queryBldr.addWhereAttrGreaterValue(CIProducts.TransactionInOutAbstract.Date, dateFrom.minusSeconds(1));
            if (!listStorage.isEmpty()) {
                queryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Storage, listStorage.toArray());
            } else {
                queryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Storage, 0);
                SalesKardexReport_Base.LOG.debug("Report not contains storage");
            }
            addAttrQuery2InitKardex(_parameter, queryBldr);
            queryBldr.addOrderByAttributeAsc(CIProducts.TransactionInOutAbstract.Date);
            queryBldr.addOrderByAttributeAsc(CIProducts.TransactionInOutAbstract.Position);
            final MultiPrintQuery multi = queryBldr.getPrint();
            multi.addAttribute(CIProducts.TransactionInOutAbstract.Date,
                            CIProducts.TransactionInOutAbstract.Quantity,
                            CIProducts.TransactionInOutAbstract.UoM);
            multi.addSelect(selTransDocInst, selTransDocName);
            multi.setEnforceSorted(true);
            multi.execute();
            while (multi.next()) {
                final Map<String, Object> map = new HashMap<String, Object>();
                final DateTime dateTrans = multi.<DateTime>getAttribute(CIProducts.TransactionInOutAbstract.Date);
                final Long uoMId = multi.<Long>getAttribute(CIProducts.TransactionInOutAbstract.UoM);
                final UoM uoM = Dimension.getUoM(uoMId);
                BigDecimal quantity = multi.<BigDecimal>getAttribute(CIProducts.TransactionInOutAbstract.Quantity);

                quantity = quantity.multiply(
                                new BigDecimal(uoM.getNumerator()).divide(new BigDecimal(uoM.getDenominator())))
                                .setScale(2, BigDecimal.ROUND_HALF_UP);

                final Instance transDocInst = multi.<Instance>getSelect(selTransDocInst);
                final String transDocName = multi.<String>getSelect(selTransDocName);

                map.put(Field.TRANS_DATE.getKey(), dateTrans);
                if (multi.getCurrentInstance().getType().equals(CIProducts.TransactionInbound.getType())
                                || multi.getCurrentInstance().getType()
                                                .equals(CIProducts.TransactionInbound4StaticStorage.getType())) {
                    map.put(Field.TRANS_INBOUND_QUANTITY.getKey(), quantity);
                    final TransDoc docTransIn = getTransDoc(_parameter, transDocInst, dateTrans, prodInst);
                    addMap2DocumentTypeIn(_parameter, map, docTransIn, currencyInst);
                } else {
                    map.put(Field.TRANS_OUTBOUND_QUANTITY.getKey(), quantity);
                    map.put(Field.TRANS_DOC_SERIE.getKey(), getSeriesOrNumberDocOut(_parameter, transDocName, true));
                    map.put(Field.TRANS_DOC_NUMBER.getKey(), getSeriesOrNumberDocOut(_parameter, transDocName, false));
                    addMap2DocumentTypeOut(_parameter, map, transDocInst);
                }
                addMap2TransactionInfo(_parameter, map, multi.getCurrentInstance());
                add2Map4ProductInfo(_parameter, map, multi.getCurrentInstance(), prodInst);
                values.add(map);
            }
        }
        if (_prodInsts != null && _prodInsts.length > 1)
        {
            values.add(new HashMap<String, Object>());
        }
        getValues().addAll(values);
    }

    /**
     * @param _parameter        Parameter as passed by the eFaps API
     * @param _map              map to add to
     * @param _transInstance    instance of the transaction
     * @param _prodInst         instance of the product
     * @throws EFapsException on error
     */
    protected void add2Map4ProductInfo(final Parameter _parameter,
                                       final Map<String, Object> _map,
                                       final Instance _transInstance,
                                       final Instance _prodInst)
        throws EFapsException
    {

    }


    @Override
    public void init(final JasperReport _jasperReport,
                     final Parameter _parameter,
                     final JRDataSource _parentSource,
                     final Map<String, Object> _jrParameters)
        throws EFapsException
    {
        final Instance product = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.product.name));
        if (product.isValid()) {
            init(_jasperReport, _parameter, _parentSource, _jrParameters, product);
        }
    }

    /**
     * @param _parameter        Parameter as passed by the eFaps API
     * @param _map              map to add to
     * @param _transInstance    instance of the transaction
     * @throws EFapsException on error
     */
    protected void addMap2TransactionInfo(final Parameter _parameter,
                                          final Map<String, Object> _map,
                                          final Instance _transInstance)
        throws EFapsException
    {
        // to be implemented
    }

    protected Map<String, Object> getInventoryValue(final Parameter _parameter,
                                                    final Instance _product,
                                                    final List<Instance> _storageInstList,
                                                    final DateTime _dateFrom,
                                                    final Instance _currencyInst)
        throws EFapsException
    {
        BigDecimal actualInventory = BigDecimal.ZERO;
        SalesKardexReport_Base.LOG.debug("Start evaluate inventory");
        final QueryBuilder queryBldr = new QueryBuilder(CIProducts.Inventory);
        if (!_storageInstList.isEmpty()) {
            queryBldr.addWhereAttrEqValue(CIProducts.Inventory.Storage, _storageInstList.toArray());
        } else {
            queryBldr.addWhereAttrEqValue(CIProducts.Inventory.Storage, 0);
            SalesKardexReport_Base.LOG.debug("Report not contains storage");
        }
        queryBldr.addWhereAttrEqValue(CIProducts.Inventory.Product, _product);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIProducts.Inventory.Quantity);
        multi.execute();
        while (multi.next()) {
            final BigDecimal quantity = multi.<BigDecimal>getAttribute(CIProducts.Inventory.Quantity);
            actualInventory = actualInventory.add(quantity);
            SalesKardexReport_Base.LOG.debug("Inventory: {}", actualInventory);
        }

        final QueryBuilder transQueryBldr = new QueryBuilder(CIProducts.TransactionInOutAbstract);
        if (!_storageInstList.isEmpty()) {
            transQueryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Storage, _storageInstList.toArray());
        } else {
            transQueryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Storage, 0);
            SalesKardexReport_Base.LOG.debug("Report not contains storage");
        }
        transQueryBldr.addWhereAttrEqValue(CIProducts.TransactionInOutAbstract.Product, _product);
        transQueryBldr.addWhereAttrGreaterValue(CIProducts.TransactionInOutAbstract.Date, _dateFrom.minusMinutes(1));
        addAttrQuery2InventoryValue(_parameter, transQueryBldr);
        final MultiPrintQuery transMulti = transQueryBldr.getPrint();
        transMulti.addAttribute(CIProducts.TransactionInOutAbstract.Quantity,
                        CIProducts.TransactionInOutAbstract.UoM);
        transMulti.execute();
        while (transMulti.next()) {
            BigDecimal quantity = transMulti.<BigDecimal>getAttribute(CIProducts.TransactionInbound.Quantity);
            final Long uoMId = transMulti.<Long>getAttribute(CIProducts.TransactionInbound.UoM);
            final UoM uoM = Dimension.getUoM(uoMId);
            quantity = quantity.multiply(new BigDecimal(uoM.getNumerator())
                            .divide(new BigDecimal(uoM.getDenominator())));
            final Instance inst = transMulti.getCurrentInstance();
            if (inst.getType().equals(CIProducts.TransactionInbound.getType())
                            || inst.getType().equals(CIProducts.TransactionInbound4StaticStorage.getType())) {
                quantity = quantity.negate();
            }
            actualInventory = actualInventory.add(quantity);
            SalesKardexReport_Base.LOG.debug("Transaction: {} ,Inventory: {}", quantity, actualInventory);
        }

        final Map<String, Object> map = new HashMap<String, Object>();
        map.put(Field.TRANS_DOC_OPERATION.getKey(), "16");
        map.put(Field.TRANS_INBOUND_QUANTITY.getKey(), actualInventory);
        map.put(Field.TRANS_INBOUND_COST.getKey(), getCost(_parameter, _product, _dateFrom.minusDays(1), _currencyInst));
        add2Map4ProductInfo(_parameter, map, null, _product);
        return map;
    }

    protected void addAttrQuery2InventoryValue(final Parameter _parameter,
                                               final QueryBuilder _queryBldr)
        throws EFapsException
    {
        // to be implemented
    }

    protected void addAttrQuery2InitKardex(final Parameter _parameter,
                                           final QueryBuilder _queryBldr)
        throws EFapsException
    {
        // to be implemented
    }

    protected List<Instance> getStorageInstList(final Parameter _parameter)
        throws EFapsException
    {
        final List<Instance> ret;
        Instance storageInst = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.storage.name));
        if (!storageInst.isValid()) {
            storageInst = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.storageGroup.name));
        }

        if (storageInst.getType().isKindOf(CIProducts.StorageGroupAbstract.getType())) {
            final QueryBuilder attrQueryBldr = new QueryBuilder(CIProducts.StorageGroupAbstract2StorageAbstract);
            attrQueryBldr.addWhereAttrEqValue(CIProducts.StorageGroupAbstract2StorageAbstract.FromAbstractLink,
                            storageInst);
            final AttributeQuery attrQuery = attrQueryBldr.getAttributeQuery(
                            CIProducts.StorageGroupAbstract2StorageAbstract.ToAbstractLink);

            final QueryBuilder queryBldr = new QueryBuilder(CIProducts.StorageAbstract);
            queryBldr.addWhereAttrInQuery(CIProducts.StorageAbstract.ID, attrQuery);
            final InstanceQuery query = queryBldr.getQuery();
            ret = query.execute();

        } else {
            ret = new ArrayList<>();
            ret.add(storageInst);
        }
        return ret;
    }

    protected BigDecimal getCost(final Parameter _parameter,
                                 final Instance _productInstance,
                                 final DateTime _dateFrom,
                                 final Instance _currencyinst)
        throws EFapsException
    {
        return Cost.getCost4Currency(_parameter, _dateFrom, _productInstance, _currencyinst);
    }

    protected void addMap2DocumentTypeIn(final Parameter _parameter,
                                         final Map<String, Object> _map,
                                         final TransDoc _docTransIn,
                                         final Instance _currencyinst)
        throws EFapsException
    {

        _map.put(Field.TRANS_DOC_TYPE.getKey(), _docTransIn.getDocType());
        _map.put(Field.TRANS_INBOUND_COST.getKey(), _docTransIn.getCostDoc()
                        .getCost(_parameter, _currencyinst, _docTransIn.getDate()));
        _map.put(Field.TRANS_DOC_SERIE.getKey(),
                        getSeriesOrNumberDocOut(_parameter, _docTransIn.getCostDocName(), true));
        _map.put(Field.TRANS_DOC_NUMBER.getKey(),
                        getSeriesOrNumberDocOut(_parameter, _docTransIn.getCostDocName(), false));
        _map.put(Field.TRANS_DOC_OPERATION.getKey(), _docTransIn.getProdDocType());
    }

    protected String getSeriesOrNumberDocOut(final Parameter _parameter,
                                             final String _name,
                                             final boolean _suffix)
    {
        String ret = "-";
        if (_name != null) {
            final String[] arrays = _name.split("-");

            if (arrays != null && arrays.length > 1) {
                if (arrays.length == 2) {
                    if (_suffix) {
                        if (arrays[0].matches("^\\d+")) {
                            ret = arrays[0];
                        }
                    } else {
                        if (!arrays[0].matches("^\\d+")) {
                            ret = _name;
                        } else {
                            ret = arrays[1];
                        }
                    }
                } else if (arrays.length == 3) {
                    if (_suffix) {
                        if (arrays[1].matches("^\\d+")) {
                            ret = arrays[1];
                        }
                    } else {
                        if (!arrays[1].matches("^\\d+")) {
                            ret = _name;
                        } else {
                            ret = arrays[2];
                        }
                    }
                } else {
                    if (!_suffix) {
                        ret = _name;
                    }
                }
            }
        }
        return ret.trim();
    }

    protected void addMap2DocumentTypeOut(final Parameter _parameter,
                                          final Map<String, Object> _map,
                                          final Instance _transDocInst)
        throws EFapsException
    {
        String ret = "-";

        final SelectBuilder selDocTypeLink = new SelectBuilder()
                        .linkto(CISales.Document2DocumentType.DocumentTypeLink);
        final SelectBuilder selDocTypeLinkName = new SelectBuilder(selDocTypeLink).attribute(CIERP.DocumentType.Name);
        final SelectBuilder selDocTypeLinkType = new SelectBuilder(selDocTypeLink).type();

        final QueryBuilder queryBldr = new QueryBuilder(CISales.Document2DocumentType);
        queryBldr.addWhereAttrEqValue(CISales.Document2DocumentType.DocumentLink, _transDocInst);
        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addSelect(selDocTypeLinkType, selDocTypeLinkName);
        multi.execute();
        while (multi.next()) {
            if (multi.<Type>getSelect(selDocTypeLinkType).equals(CISales.ProductDocumentType.getType())) {
                ret = multi.<String>getSelect(selDocTypeLinkName);
            }
        }
        if (SalesKardexReport_Base.DOCTYPE_MAP.containsKey(_transDocInst.getType().getId())) {
            _map.put(Field.TRANS_DOC_TYPE.getKey(),
                            SalesKardexReport_Base.DOCTYPE_MAP.get(_transDocInst.getType().getId()));
        } else {
            _map.put(Field.TRANS_DOC_TYPE.getKey(), "-");
        }
        _map.put(Field.TRANS_DOC_OPERATION.getKey(), ret);
    }

    protected void addReport2Parameter(final Parameter _parameter,
                                       final StandartReport _report)
        throws EFapsException
    {
        // TODO Auto-generated method stub
    }

    public Return createReport(final Parameter _parameter)
        throws EFapsException
    {
        final String dateFrom = _parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.dateFrom.name);
        final String dateTo = _parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.dateTo.name);
        final Instance productInst = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.product.name));
        final Instance storageGroupInst = Instance.get(_parameter
                        .getParameterValue(CIFormSales.Sales_Products_Kardex_OfficialReportForm.storage.name));
        final DateTime from = new DateTime(dateFrom);
        final DateTime to = new DateTime(dateTo);

        final StandartReport report = new StandartReport();
        report.setFileName(getReportName(_parameter, from, to));
        report.getJrParameters().put("Periode", shortTimes(from, to));
        final SystemConfiguration config = ERP.getSysConfig();
        if (config != null) {
            final String companyName = config.getAttributeValue(ERPSettings.COMPANYNAME);
            final String companyTaxNumb = config.getAttributeValue(ERPSettings.COMPANYTAX);

            if (companyName != null && companyTaxNumb != null && !companyName.isEmpty() && !companyTaxNumb.isEmpty()) {
                report.getJrParameters().put("CompanyName", companyName);
                report.getJrParameters().put("CompanyTaxNum", companyTaxNumb);
            }
        }
        if (productInst.isValid()) {
            final ProductKardex product = new ProductKardex(productInst);
            report.getJrParameters().put("ProductName", product.getProductName());
            report.getJrParameters().put("ProductDesc", product.getProductDesc());
            report.getJrParameters().put("ProductUoM", product.getProductUoM());
            report.getJrParameters().put("ProductExistType", product.getProductExistType());
        }
        if (storageGroupInst.isValid()) {
            final PrintQuery print = new PrintQuery(storageGroupInst);
            print.addAttribute(CIProducts.StorageGroupAbstract.Name);
            print.execute();

            report.getJrParameters().put("StorageName",
                            print.<String>getAttribute(CIProducts.StorageGroupAbstract.Name));
        }
        addReport2Parameter(_parameter, report);

        return report.execute(_parameter);
    }

    /**
     * Get the name for the report.
     *
     * @param _parameter Parameter as passed form the eFaps API
     * @param _from fromdate
     * @param _to to date
     * @return name of the report
     */
    protected String getReportName(final Parameter _parameter,
                                   final DateTime _from,
                                   final DateTime _to)
    {
        return DBProperties.getProperty("Sales_Products_KardexOfficial.Label", "es") + "-" + shortTimes(_from, _to);
    }

    protected String shortTimes(final DateTime _from,
                                final DateTime _to)
    {
        return _from.toString(DateTimeFormat.shortDate()) + " - " + _to.toString(DateTimeFormat.shortDate());
    }


    protected TransDoc getTransDoc(final Parameter _parameter,
                                   final Instance _transDocInst,
                                   final DateTime _date,
                                   final Instance _product)
        throws EFapsException
    {
        return new TransDoc(_transDocInst, _date, _product);
    }



    public class ProductKardex
    {

        private final Instance product;

        private String name;

        private String description;

        private String uoM;

        public ProductKardex(final Instance _prodInst)
            throws EFapsException
        {
            this.product = _prodInst;
            if (getInstance().isValid()) {
                final PrintQuery print = CachedPrintQuery.get4Request(_prodInst);
                print.addAttribute(CIProducts.ProductAbstract.Name,
                                CIProducts.ProductAbstract.Description,
                                CIProducts.ProductAbstract.Dimension);
                print.execute();

                this.name = print.<String>getAttribute(CIProducts.ProductAbstract.Name);
                this.description = print.<String>getAttribute(CIProducts.ProductAbstract.Description);
                this.uoM = Dimension.get(print.<Long>getAttribute(CIProducts.ProductAbstract.Dimension))
                                .getBaseUoM().getName();
            }
        }

        protected Instance getInstance()
        {
            return this.product;
        }

        protected String getProductName()
        {
            return this.name;
        }

        protected String getProductDesc()
        {
            return this.description;
        }

        protected String getProductUoM()
        {
            return this.uoM;
        }

        protected String getProductExistType()
            throws EFapsException
        {
            final StringBuilder ret = new StringBuilder();
            if (SalesKardexReport_Base.EXISTTYPE_MAP.containsKey(this.product.getType().getId())) {
                ret.append(SalesKardexReport_Base.EXISTTYPE_MAP.get(this.product.getType().getId()));
            }

            return ret.toString();
        }
    }

    public class TransDoc
    {

        protected final Instance instance;

        protected  final ProductKardex product;

        protected CostDoc costDoc;

        protected String costDocName;

        protected String prodDocType;

        protected String docType;

        private DateTime date;

        public TransDoc(final Instance _transDocInst,
                        final DateTime _date,
                        final Instance _product)
            throws EFapsException
        {
            this.date = _date;
            this.instance = _transDocInst;
            this.product = new ProductKardex(_product);
        }

        /**
         * @return
         */
        public String getProdDocType()
            throws EFapsException
        {
            if (this.prodDocType == null && getInstance() != null && getInstance().isValid()) {
                final PrintQuery print = CachedPrintQuery.get4Request(getInstance());
                final SelectBuilder sel = SelectBuilder.get()
                                .linkfrom(CISales.Document2ProductDocumentType.DocumentLink)
                                .linkto(CISales.Document2ProductDocumentType.DocumentTypeLink)
                                .attribute(CISales.ProductDocumentType.Name);
                print.addSelect(sel);
                print.execute();
                this.prodDocType = print.getSelect(sel);
            }
            return this.prodDocType;
        }

        /**
         * @return
         */
        public String getDocType()
            throws EFapsException
        {
            if (this.docType == null && getCostDoc().getCostDocInst() != null
                            && getCostDoc().getCostDocInst().isValid()) {
                final PrintQuery print = CachedPrintQuery.get4Request(getCostDoc().getCostDocInst());
                final SelectBuilder sel = SelectBuilder.get().linkfrom(CISales.Document2DocumentType.DocumentLink)
                                .linkto(CISales.Document2DocumentType.DocumentTypeLink)
                                .attribute(CIERP.DocumentType.Name);
                print.addSelect(sel);
                print.execute();
                this.docType = print.getSelect(sel);

            }
            if (this.docType == null && getInstance() != null && getInstance().isValid()) {
                this.docType = DOCTYPE_MAP.get(getInstance().getType().getId());
            }
            return this.docType;
        }

        protected Instance getInstance()
        {
            return this.instance;
        }

        protected CostDoc getCostDoc()
            throws EFapsException
        {
            if (this.costDoc == null) {
                this.costDoc = new CostDoc(getInstance(), this.product.getInstance());
            }
            return this.costDoc;
        }

        protected String getCostDocName()
            throws EFapsException
        {
            if (this.costDocName == null && getCostDoc().getCostDocInst() != null
                            && getCostDoc().getCostDocInst().isValid()) {
                final PrintQuery print = new PrintQuery(getCostDoc().getCostDocInst());
                print.addAttribute(CIERP.DocumentAbstract.Name);
                print.execute();
                this.costDocName = print.getAttribute(CIERP.DocumentAbstract.Name);
            }
            return this.costDocName;
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
    }
}
