# ProductComparison


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**productId** | **string** |  | [default to undefined]
**productName** | **string** |  | [default to undefined]
**renewalType** | **string** |  | [optional] [default to undefined]
**coverages** | [**Array&lt;CoverageItem&gt;**](CoverageItem.md) |  | [default to undefined]
**exemptions** | [**Array&lt;ExemptionItem&gt;**](ExemptionItem.md) |  | [default to undefined]
**premiumExample** | [**PremiumComparisonItem**](PremiumComparisonItem.md) |  | [optional] [default to undefined]
**surrenderValueSample** | [**SurrenderComparisonItem**](SurrenderComparisonItem.md) |  | [optional] [default to undefined]
**insurancePeriod** | **string** |  | [optional] [default to undefined]

## Example

```typescript
import { ProductComparison } from './api';

const instance: ProductComparison = {
    productId,
    productName,
    renewalType,
    coverages,
    exemptions,
    premiumExample,
    surrenderValueSample,
    insurancePeriod,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
