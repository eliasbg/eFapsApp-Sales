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

package org.efaps.esjp.sales;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.wicket.datetime.StyleDateConverter;
import org.efaps.admin.common.SystemConfiguration;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Parameter.ParameterValues;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.db.Instance;
import org.efaps.db.InstanceQuery;
import org.efaps.db.MultiPrintQuery;
import org.efaps.db.PrintQuery;
import org.efaps.db.QueryBuilder;
import org.efaps.esjp.ci.CIERP;
import org.efaps.esjp.ci.CIProducts;
import org.efaps.esjp.erp.Currency;
import org.efaps.esjp.sales.document.AbstractDocument_Base;
import org.efaps.esjp.ui.html.HtmlTable;
import org.efaps.ui.wicket.util.DateUtil;
import org.efaps.ui.wicket.util.EFapsKey;
import org.efaps.util.EFapsException;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id$
 */
@EFapsUUID("3a726661-473f-48b6-82dd-9b6498561d48")
@EFapsRevision("$Rev$")
public abstract class PriceUtil_Base
    implements Serializable
{

    /**
     * Needed for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Method to get the Price for a product.
     *
     * @param _parameter    Parameter as passed form the efaps API
     * @param _oid          oid of the product the price is wanted for
     * @param _typeUUID     uuid of th eprice type wanted
     * @return price for the product as BigDecimal
     * @throws EFapsException on error
     */
    public ProductPrice getPrice(final Parameter _parameter,
                                 final String _oid,
                                 final UUID _typeUUID)
        throws EFapsException
    {
        final ProductPrice ret = new ProductPrice();

        final DateTime date = getDateFromParameter(_parameter);

        final QueryBuilder queryBldr = new QueryBuilder(_typeUUID);
        queryBldr.addWhereAttrEqValue(CIProducts.ProductPricelistPurchase.ProductAbstractLink,
                        Instance.get(_oid).getId());
        queryBldr.addWhereAttrLessValue(CIProducts.ProductPricelistPurchase.ValidFrom, date.plusSeconds(1));
        queryBldr.addWhereAttrGreaterValue(CIProducts.ProductPricelistPurchase.ValidUntil, date.minusSeconds(1));
        final InstanceQuery query = queryBldr.getQuery();
        query.execute();
        if (query.next()) {
            final QueryBuilder queryBldr2 = new QueryBuilder(CIProducts.ProductPricelistPosition);
            queryBldr2.addWhereAttrEqValue(CIProducts.ProductPricelistPosition.ProductPricelist,
                                          query.getCurrentValue().getId());
            final MultiPrintQuery multi = queryBldr2.getPrint();
            multi.addAttribute(CIProducts.ProductPricelistPosition.Price,
                               CIProducts.ProductPricelistPosition.CurrencyId);
            multi.execute();
            if (multi.next()) {
                final Instance priceInst = Instance.get(CIERP.Currency.getType(),
                                             multi.<Long>getAttribute(CIProducts.ProductPricelistPosition.CurrencyId));
                final Instance currentInst = (Instance) Context.getThreadContext().getSessionAttribute(
                                AbstractDocument_Base.CURRENCY_INSTANCE_KEY);
                // Sales-Configuration
                final Instance baseInst = SystemConfiguration.get(UUID
                                .fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f")).getLink("CurrencyBase");
                final BigDecimal price = multi.<BigDecimal>getAttribute(CIProducts.ProductPricelistPosition.Price);

                ret.setCurrentCurrencyInstance(currentInst);
                ret.setOrigCurrencyInstance(priceInst);
                ret.setOrigPrice(price);
                if (priceInst.equals(currentInst)) {
                    ret.setCurrentPrice(price);
                } else {
                    if (currentInst != null) {
                        final BigDecimal[] rates = getRates(_parameter, currentInst, priceInst);
                        ret.setCurrentPrice(price.multiply(rates[2]));
                    } else {
                        ret.setCurrentPrice(price);
                    }
                }
                if (priceInst.equals(baseInst)) {
                    ret.setBasePrice(price);
                    ret.setBaseRate(BigDecimal.ONE);
                } else {
                    final BigDecimal[] rates = getRates(_parameter, currentInst, baseInst);
                    ret.setBasePrice(price.multiply(rates[2]));
                    ret.setBaseRate(rates[2]);
                }
            }
        }
        return ret;
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API for esjp
     * @param _targetCurrencyInst instance of the target Currency
     * @param _currentCurrencyInst instance of the current Currency
     * @return Array with rates for the date evaluated from the given _parameter
     *         {targetRate (rate for _targetCurrencyInst), currentRate (rate for
     *         _currentCurrencyInst, exchangeRate (targetRate/currentRate)}
     * @throws EFapsException on error
     */
    public BigDecimal[] getRates(final Parameter _parameter,
                                 final Instance _targetCurrencyInst,
                                 final Instance _currentCurrencyInst)
        throws EFapsException
    {
        return getRates(getDateFromParameter(_parameter), _targetCurrencyInst, _currentCurrencyInst);
    }

    /**
     * Returns an Array of rates.:
     * <ol>
     *  <li>new Rate used for Calculation</li>
     *  <li>current Rate used for Calculation</li>
     *  <li>rate used to convert the current rate into the new rate</li>
     *  <li>new Rate as the value for the UserInterface</li>
     * </ol>
     * @param _date date the rates will be retrieved for
     * @param _targetCurrencyInst instance of the target Currency
     * @param _currentCurrencyInst instance of the current Currency
     * @return Array with rates for the date {targetRate (rate for
     *         _targetCurrencyInst), currentRate (rate for _currentCurrencyInst,
     *         exchangeRate (targetRate/currentRate)}
     * @throws EFapsException on error
     */
    public BigDecimal[] getRates(final DateTime _date,
                                 final Instance _targetCurrencyInst,
                                 final Instance _currentCurrencyInst)
        throws EFapsException
    {
        // Sales-Configuration
        final Instance baseInst = SystemConfiguration.get(UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f"))
                        .getLink("CurrencyBase");
        BigDecimal newRate;
        BigDecimal currentRate;
        BigDecimal newRateUI;
        if (_targetCurrencyInst.equals(baseInst)) {
            newRate = BigDecimal.ONE;
            newRateUI = newRate;
        } else {
            final BigDecimal[] rates = getExchangeRate(_date, _targetCurrencyInst);
            newRate = rates[0];
            newRateUI = rates[1];
            if (newRate.compareTo(BigDecimal.ZERO) == 0) {
                newRate = BigDecimal.ONE;
                newRateUI = BigDecimal.ONE;
            }
        }
        if (_currentCurrencyInst.equals(baseInst)) {
            currentRate = BigDecimal.ONE;
        } else {
            currentRate = getExchangeRate(_date, _currentCurrencyInst)[0];
            if (currentRate.compareTo(BigDecimal.ZERO) == 0) {
                currentRate = BigDecimal.ONE;
            }
        }
        return new BigDecimal[]{ newRate, currentRate,
                        newRate.divide(currentRate, 12, RoundingMode.HALF_UP), newRateUI};
    }

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return exchange rate
     * @throws EFapsException on error
     */
    public BigDecimal getExchangeRate(final Parameter _parameter)
        throws EFapsException
    {
        return getExchangeRate(getDateFromParameter(_parameter));
    }

    /**
     * Method to get the exchange rate for the currency from the sales
     * SystemConfiguration for a specific date.
     *
     * @param _date date the rate is wanted for.
     * @return rate
     * @throws EFapsException on error
     */
    public BigDecimal getExchangeRate(final DateTime _date)
        throws EFapsException
    {
        final Instance curInstance = SystemConfiguration.get(UUID.fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f"))
                        .getLink("Currency4Invoice");
        return getExchangeRate(_date, curInstance)[0];
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @param _curInstance instrance of the currency
     * @return exchange rate
     * @throws EFapsException on error
     */
    public BigDecimal getExchangeRate(final Parameter _parameter,
                                      final Instance _curInstance)
        throws EFapsException
    {
        return getExchangeRate(getDateFromParameter(_parameter), _curInstance)[0];
    }

    /**
     * Method to get the exchange rate for the currency from the sales
     * SystemConfiguration for a specific date.
     * Returns an Array of rates:
     * <ol>
     *  <li>Rate used for Calculation</li>
     *  <li>Rate as the value for the UserInterface</li>
     * </ol>
     * @param _date date the rate is wanted for.
     * @param _curInstance instance of a currency the rate is wanted for
     * @return rateArray
     * @throws EFapsException on error
     */
    public BigDecimal[] getExchangeRate(final DateTime _date,
                                        final Instance _curInstance)
        throws EFapsException
    {
        final QueryBuilder queryBldr = new QueryBuilder(CIERP.CurrencyRateClient);
        queryBldr.addWhereAttrEqValue(CIERP.CurrencyRateClient.CurrencyLink, _curInstance.getId());
        queryBldr.addWhereAttrLessValue(CIERP.CurrencyRateClient.ValidFrom, _date.plusSeconds(1));
        queryBldr.addWhereAttrGreaterValue(CIERP.CurrencyRateClient.ValidUntil, _date.minusSeconds(1));

        final MultiPrintQuery multi = queryBldr.getPrint();
        multi.addAttribute(CIERP.CurrencyRateClient.Rate);
        multi.execute();
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal rateUI = BigDecimal.ONE;

        if (multi.next()) {
            rate = new Currency().evalRate(multi.<Object[]>getAttribute(CIERP.CurrencyRateClient.Rate), false);
            rateUI = new Currency().evalRate(multi.<Object[]>getAttribute(CIERP.CurrencyRateClient.Rate), true);
        }
        return new BigDecimal[] { rate, rateUI };
    }

    /**
     * @param _parameter Parameter as passed by the eFasp API
     * @return new DateTime
     * @throws EFapsException on error
     */
    public DateTime getDateFromParameter(final Parameter _parameter)
        throws EFapsException
    {
        final String dateStr = _parameter.getParameterValue("date_eFapsDate");
        final DateTime date;
        if (dateStr != null && dateStr.length() > 0) {
            date = DateUtil.getDateFromParameter(dateStr);
        } else {
            date = new DateTime();
        }
        return date;
    }

    /**
     * @param _calc calculator the format is wanted for
     * @return  Decimal Format
     * @throws EFapsException on error
     */
    protected DecimalFormat getDigitsformater()
        throws EFapsException
    {
        final DecimalFormat formater = (DecimalFormat) NumberFormat.getInstance(Context.getThreadContext().getLocale());
        formater.setMaximumFractionDigits(2);
        formater.setMinimumFractionDigits(2);
        formater.setRoundingMode(RoundingMode.HALF_UP);
        formater.setParseBigDecimal(true);
        return formater;
    }

    protected DateTimeFormatter getDateFormat (final String _style) throws EFapsException {
        final StyleDateConverter styledate = new StyleDateConverter(false);
        DateTimeFormatter fmt = DateTimeFormat.forPattern(styledate.getDatePattern());
        if (_style != null) {
            fmt = DateTimeFormat.forPattern(_style);
        }
        fmt.withLocale(Context.getThreadContext().getLocale());

        return fmt;
    }

    /**
     * @param _parameter
     * @return
     * @throws EFapsException
     */
    public Return getPriceListHistory(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final Map<String, List<String>> mapProd = new HashMap<String, List<String>>();
        final Map<String, String> map = new HashMap<String, String>();
        final Map<?, ?> props = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final Map<?, ?> others = (HashMap<?, ?>) _parameter.get(ParameterValues.OTHERS);
        final String[] childOids = (String[]) others.get("selectedRow");
        if (props.containsKey("Interval") && props.containsKey("Range")) {
            final String[] range = ((String) props.get("Range")).split(":");
            final String interval = (String) props.get("Interval");

            final List<String> heads = getPrices4Product(_parameter, range, interval, null);
            for (final String oid : childOids) {
                final PrintQuery print = new PrintQuery(oid);
                print.addAttribute(CIProducts.ProductAbstract.Name, CIProducts.ProductAbstract.Description);
                if (print.execute()) {
                    final List<String> lstPrice =
                                        getPrices4Product(_parameter, range, interval, print.getCurrentInstance());
                    mapProd.put(print.<String>getAttribute(CIProducts.ProductAbstract.Name) + " - "
                            + print.<String>getAttribute(CIProducts.ProductAbstract.Description), lstPrice);
                }
            }

            final String html = getTable4PriceListHistory(mapProd, heads);
            map.put(EFapsKey.PICKER_JAVASCRIPT.getKey(), "document.getElementsByName('priceHistory')[0].innerHTML='" + html + "';");
            ret.put(ReturnValues.VALUES, map);
        }

        return ret;
    }

    protected List<String> getPrices4Product(final Parameter _parameter,
                                                         final String[] _range,
                                                         final String _interval,
                                                         final Instance _instanceProduct)
        throws EFapsException
    {
        @SuppressWarnings("unchecked")
        final Map<String, String[]> parameters = (Map<String, String[]>) _parameter.get(ParameterValues.PARAMETERS);
        final List<String> lstPrice = new ArrayList<String>();
        final DateTimeFormatter fmt = getDateFormat(null);
        final DateTimeFormatter fmt2 = getDateFormat("MM/dd");

        if (_range[0].equals(PriceUtil_Base.RangeInterval.MONTH.getKey())) {
            final Integer months = Integer.parseInt(_range[1]);
            if (_interval.equals(PriceUtil_Base.RangeInterval.WEEK.getKey())) {
                final DateTime dateMinusRange = new DateTime().minusMonths(months);
                final DateTime dateFrom = dateMinusRange.minusDays(dateMinusRange.getDayOfMonth() - 1);
                final DateTime dateTo = new DateTime();
                DateTime dateCont = dateFrom;
                while (dateCont.getWeekOfWeekyear() <= dateTo.getWeekOfWeekyear()) {
                    final DateTime dateIni = dateCont;
                    int contDays = dateCont.getDayOfWeek();
                    final ProductPriceList priceInter = new ProductPriceList();
                    int daysLimit = 7;
                    if (dateCont.getWeekOfWeekyear() == dateTo.getWeekOfWeekyear()) {
                        daysLimit = dateTo.getDayOfWeek();
                    }
                    while (contDays <= daysLimit) {
                        parameters.put("date_eFapsDate", new String[] { dateCont.toString(fmt) });
                        if (_instanceProduct != null) {
                            final ProductPrice price = getPrice(_parameter, _instanceProduct.getOid(),
                                                CIProducts.ProductPricelistRetail.uuid);
                            priceInter.setLstInterval(price.getBasePrice());
                        }
                        dateCont = dateCont.plusDays(1);
                        contDays++;
                    }
                    if (_instanceProduct != null) {
                        final String average = priceInter.getPricesAverage();
                        lstPrice.add(average);
                    } else {
                        lstPrice.add(dateIni.toString(fmt2) + "-<br>" + dateCont.minusDays(1).toString(fmt2));
                    }
                    if (dateIni.getWeekOfWeekyear() == dateTo.getWeekOfWeekyear()) {
                        dateCont = dateCont.plusWeeks(1);
                    }
                }
            }
        } else if (_range[0].equals(PriceUtil_Base.RangeInterval.YEAR.getKey())) {

        }

        return lstPrice;
    }

    protected String getTable4PriceListHistory(final Map<String, List<String>> _products,
                                               final List<String> _heads)
    {
        final HtmlTable htmlT = new HtmlTable();
        htmlT.table();
        htmlT.tr();
        htmlT.th("Producto");
        for (final String head : _heads) {
            htmlT.th(head);
        }
        htmlT.trC();
        for (final Entry<String, List<String>> entry : _products.entrySet()) {
            htmlT.tr();
            htmlT.td(entry.getKey());
            for (final String entry2 : entry.getValue()) {
                htmlT.td(entry2);
            }
            htmlT.trC();
        }
        htmlT.tableC();

        return htmlT.toString();
    }

    public Return getProductsList (final Parameter _parameter) throws EFapsException {
        final List<Instance> map = new ArrayList<Instance>();
        final Map<?, ?> properties = (Map<?, ?>) _parameter.get(ParameterValues.PROPERTIES);
        final String typesStr = (String) properties.get("Types");
        final String[] types = typesStr.split(";");
        for (final String type : types) {
            final QueryBuilder queryBldr = new QueryBuilder(Type.get(type));
            final InstanceQuery query = queryBldr.getQuery();
            query.execute();
            while (query.next()) {
                map.add(query.getCurrentValue());
            }
        }
        final Return ret = new Return();
        ret.put(ReturnValues.VALUES, map);
        return ret;
    }

    /**
     * Represent the price for one product.
     */
    public class ProductPrice
        implements Serializable
    {

        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Instance of the original Currency.
         */
        private Instance origCurrencyInstance;

        /**
         * Original Price.
         */
        private BigDecimal origPrice = BigDecimal.ZERO;

        /**
         * Instance of the current Currency.
         */
        private Instance currentCurrencyInstance;

        /**
         * Current Price.
         */
        private BigDecimal currentPrice = BigDecimal.ZERO;

        /**
         * Base Price.
         */
        private BigDecimal basePrice = BigDecimal.ZERO;

        /**
         * Exchange rate to the base.
         */
        private BigDecimal baseRate = BigDecimal.ONE;

        /**
         *
         */
        public ProductPrice()
            throws EFapsException
        {
            final Instance baseInst = SystemConfiguration.get(UUID
                        .fromString("c9a1cbc3-fd35-4463-80d2-412422a3802f")).getLink("CurrencyBase");
            this.origCurrencyInstance = baseInst;
            this.currentCurrencyInstance = baseInst;
        }

        /**
         * @param _origCurrencyInst instance of the currency
         * @param _origPrice        original price
         * @param _basePrice        base price
         */
        public ProductPrice(final Instance _origCurrencyInst,
                            final BigDecimal _origPrice,
                            final BigDecimal _basePrice)
        {
            this.origCurrencyInstance = _origCurrencyInst;
            this.origPrice = _origPrice;
            this.basePrice = _basePrice;
        }

        /**
         * Setter method for instance variable {@link #baseRate}.
         *
         * @param _baseRate value for instance variable {@link #baseRate}
         */
        public void setBaseRate(final BigDecimal _baseRate)
        {
            this.baseRate = _baseRate;

        }

        /**
         * Getter method for the instance variable {@link #baseRate}.
         *
         * @return value of instance variable {@link #baseRate}
         */
        public BigDecimal getBaseRate()
        {
            return this.baseRate;
        }

        /**
         * Setter method for instance variable {@link #basePrice}.
         *
         * @param _basePrice value for instance variable {@link #basePrice}
         */
        public void setBasePrice(final BigDecimal _basePrice)
        {
            this.basePrice = _basePrice;
        }

        /**
         * Setter method for instance variable {@link #origCurrencyInstance}.
         *
         * @param _origCurrencyInstance value for instance variable
         *            {@link #origCurrencyInstance}
         */
        public void setOrigCurrencyInstance(final Instance _origCurrencyInstance)
        {
            this.origCurrencyInstance = _origCurrencyInstance;
        }

        /**
         * Setter method for instance variable {@link #origPrice}.
         *
         * @param _origPrice value for instance variable {@link #origPrice}
         */
        public void setOrigPrice(final BigDecimal _origPrice)
        {
            this.origPrice = _origPrice;
        }

        /**
         * Getter method for the instance variable {@link #origCurrencyInstance}
         * .
         *
         * @return value of instance variable {@link #origCurrencyInstance}
         */
        public Instance getOrigCurrencyInstance()
        {
            return this.origCurrencyInstance;
        }

        /**
         * Getter method for the instance variable {@link #origPrice}.
         *
         * @return value of instance variable {@link #origPrice}
         */
        public BigDecimal getOrigPrice()
        {
            return this.origPrice;
        }

        /**
         * Getter method for the instance variable {@link #basePrice}.
         *
         * @return value of instance variable {@link #basePrice}
         */
        public BigDecimal getBasePrice()
        {
            return this.basePrice;
        }

        /**
         * Getter method for the instance variable
         * {@link #currentCurrencyInstance}.
         *
         * @return value of instance variable {@link #currentCurrencyInstance}
         */
        public Instance getCurrentCurrencyInstance()
        {
            return this.currentCurrencyInstance;
        }

        /**
         * Setter method for instance variable {@link #currentCurrencyInstance}.
         *
         * @param _currentCurrencyInstance value for instance variable
         *            {@link #currentCurrencyInstance}
         */

        public void setCurrentCurrencyInstance(final Instance _currentCurrencyInstance)
        {
            this.currentCurrencyInstance = _currentCurrencyInstance;
        }

        /**
         * Getter method for the instance variable {@link #currentPrice}.
         *
         * @return value of instance variable {@link #currentPrice}
         */
        public BigDecimal getCurrentPrice()
        {
            return this.currentPrice;
        }

        /**
         * Setter method for instance variable {@link #currentPrice}.
         *
         * @param _currentPrice value for instance variable
         *            {@link #currentPrice}
         */
        public void setCurrentPrice(final BigDecimal _currentPrice)
        {
            this.currentPrice = _currentPrice;
        }
    }

    /**
     * @author jorge
     *
     */
    public enum RangeInterval
    {
        /** */
        WEEK("week"),
        /** */
        MONTH("month"),
        /** */
        YEAR("year");

        /**
         * key.
         */
        private final String key;

        /**
         * @param _key key
         */
        private RangeInterval(final String _key)
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
     * Represent the interval of days for a range.
     */
    public class ProductPriceList
        implements Serializable
    {
        /**
         * Needed for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * List with the prices for each day of the interval.
         */
        private final List<BigDecimal> lstInterval = new ArrayList<BigDecimal>();

        /**
         * Current price.
         */
        private BigDecimal currentPrice = BigDecimal.ZERO;

        /**
         * @return List with the prices of the interval.
         */
        public List<BigDecimal> getLstInterval()
        {
            return this.lstInterval;
        }

        /**
         * @param _newPrice BigDecimal with the new price to add.
         */
        public void setLstInterval(final BigDecimal _newPrice)
        {
            if (_newPrice != null) {
                this.lstInterval.add(_newPrice);
                setCurrentPrice(_newPrice);
            } else {
                this.lstInterval.add(this.currentPrice);
            }
        }

        /**
         * @return BigDecimal with the current price.
         */
        private BigDecimal getCurrentPrice()
        {
            return this.currentPrice;
        }

        /**
         * @param _newPrice BigDecimal with the new price to add.
         */
        private void setCurrentPrice(final BigDecimal _newPrice)
        {
            if (_newPrice.compareTo(this.currentPrice) != 0) {
                this.currentPrice = _newPrice;
            }
        }

        public String getPricesAverage()
            throws EFapsException
        {
            BigDecimal totalPrice = BigDecimal.ZERO;
            for (final BigDecimal price : this.lstInterval) {
                totalPrice = totalPrice.add(price);
            }
            final Format formater = getDigitsformater();
            final BigDecimal averagePrice = totalPrice.divide(new BigDecimal(this.lstInterval.size()), BigDecimal.ROUND_HALF_UP);
            return formater.format(averagePrice);
        }

    }
}
