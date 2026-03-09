# PolicyArticleResponse


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **string** |  | [default to undefined]
**insuranceCompany** | [**CompanyInfo**](CompanyInfo.md) |  | [default to undefined]
**productId** | **string** |  | [default to undefined]
**productName** | **string** |  | [default to undefined]
**articleNumber** | **string** |  | [optional] [default to undefined]
**articleTitle** | **string** |  | [optional] [default to undefined]
**content** | **string** |  | [default to undefined]
**contentType** | **string** |  | [default to undefined]
**sectionType** | **string** |  | [optional] [default to undefined]
**effectiveDate** | **string** |  | [optional] [default to undefined]
**dataSource** | **string** |  | [optional] [default to undefined]
**dataSourceId** | **string** |  | [optional] [default to undefined]
**hasPdfSource** | **boolean** |  | [default to undefined]
**relatedArticles** | [**Array&lt;RelatedArticleResponse&gt;**](RelatedArticleResponse.md) |  | [default to undefined]

## Example

```typescript
import { PolicyArticleResponse } from './api';

const instance: PolicyArticleResponse = {
    id,
    insuranceCompany,
    productId,
    productName,
    articleNumber,
    articleTitle,
    content,
    contentType,
    sectionType,
    effectiveDate,
    dataSource,
    dataSourceId,
    hasPdfSource,
    relatedArticles,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
