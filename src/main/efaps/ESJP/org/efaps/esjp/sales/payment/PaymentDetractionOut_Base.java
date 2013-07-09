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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsRevision;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Insert;
import org.efaps.esjp.ci.CIFormSales;
import org.efaps.esjp.ci.CISales;
import org.efaps.util.EFapsException;

/**
 * TODO comment!
 *
 * @author The eFaps Team
 * @version $Id: PaymentExchange_Base.java 8156 2012-11-05 15:32:12Z
 *          jan@moxter.net $
 */
@EFapsUUID("ba14f903-9522-4a10-b847-db50fdb360a3")
@EFapsRevision("$Rev$")
public abstract class PaymentDetractionOut_Base
    extends AbstractPaymentOut
{

    /**
     * @param _parameter Parameter as passed by the eFaps API
     * @return new Return
     * @throws EFapsException on error
     */
    public Return create(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc createdDoc = createDoc(_parameter);
        createPayment(_parameter, createdDoc);
        final Return ret = createReportDoc(_parameter, createdDoc);
        return ret;
    }

    @Override
    protected CreatedDoc createDoc(final Parameter _parameter)
        throws EFapsException
    {
        final CreatedDoc ret = super.createDoc(_parameter);

        // in case of bulkpayment connect the paymentdoc to the bulkpayment
        if (_parameter.getInstance() != null
                        && _parameter.getInstance().getType().isKindOf(CISales.BulkPayment.getType())) {
            final Insert insert = new Insert(CISales.BulkPayment2PaymentDocument);
            insert.add(CISales.BulkPayment2PaymentDocument.FromLink, _parameter.getInstance().getId());
            insert.add(CISales.BulkPayment2PaymentDocument.ToLink, ret.getInstance().getId());
            final String opTypeId = _parameter
                            .getParameterValue(CIFormSales.Sales_PaymentDetractionOutForm.operationType.name);
            final String servTypeId = _parameter
                            .getParameterValue(CIFormSales.Sales_PaymentDetractionOutForm.serviceType.name);
            insert.add(CISales.BulkPayment2PaymentDocument.OperationType, opTypeId);
            insert.add(CISales.BulkPayment2PaymentDocument.ServiceType, servTypeId);
            insert.execute();
        }
        return ret;
    }
}
