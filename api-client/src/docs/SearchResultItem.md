# SearchResultItem


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**insuranceCompany** | [**CompanyInfo**](CompanyInfo.md) |  | [default to undefined]
**productId** | **string** |  | [default to undefined]
**productName** | **string** |  | [default to undefined]
**contentType** | **string** |  | [default to undefined]
**articleNumber** | **string** |  | [optional] [default to undefined]
**articleTitle** | **string** |  | [optional] [default to undefined]
**content** | **string** |  | [default to undefined]
**highlight** | **string** |  | [default to undefined]
**relevanceScore** | **number** |  | [default to undefined]
**dataSource** | **string** |  | [optional] [default to undefined]
**supplementary** | [**SupplementaryInfo**](SupplementaryInfo.md) |  | [default to undefined]

## Example

```typescript
import { SearchResultItem } from './api';

const instance: SearchResultItem = {
    id,
    insuranceCompany,
    productId,
    productName,
    contentType,
    articleNumber,
    articleTitle,
    content,
    highlight,
    relevanceScore,
    dataSource,
    supplementary,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
