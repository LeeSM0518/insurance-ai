# AIApi

All URIs are relative to *http://localhost:8888*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**createSession**](#createsession) | **POST** /api/v1/chat/sessions | 채팅 세션 생성|
|[**deleteSession**](#deletesession) | **DELETE** /api/v1/chat/sessions/{sessionId} | 채팅 세션 삭제|
|[**getMessages**](#getmessages) | **GET** /api/v1/chat/sessions/{sessionId}/messages | 채팅 메시지 이력 조회|
|[**getSessions**](#getsessions) | **GET** /api/v1/chat/sessions | 채팅 세션 목록 조회|
|[**replyToSession**](#replytosession) | **POST** /api/v1/chat/sessions/{sessionId}/reply | 채팅 세션 초기 메시지에 대한 AI 응답 (SSE 스트리밍)|
|[**sendMessage**](#sendmessage) | **POST** /api/v1/chat/sessions/{sessionId}/messages | 채팅 메시지 전송 (SSE 스트리밍)|

# **createSession**
> ServiceResponseChatSessionResponse createSession(createSessionRequest)


### Example

```typescript
import {
    AIApi,
    Configuration,
    CreateSessionRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

let createSessionRequest: CreateSessionRequest; //

const { status, data } = await apiInstance.createSession(
    createSessionRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **createSessionRequest** | **CreateSessionRequest**|  | |


### Return type

**ServiceResponseChatSessionResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**201** | Created |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteSession**
> deleteSession()


### Example

```typescript
import {
    AIApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

let sessionId: string; // (default to undefined)

const { status, data } = await apiInstance.deleteSession(
    sessionId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **sessionId** | [**string**] |  | defaults to undefined|


### Return type

void (empty response body)

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: Not defined


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**204** | No Content |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getMessages**
> ServiceResponseSessionMessagesResponse getMessages()


### Example

```typescript
import {
    AIApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

let sessionId: string; // (default to undefined)

const { status, data } = await apiInstance.getMessages(
    sessionId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **sessionId** | [**string**] |  | defaults to undefined|


### Return type

**ServiceResponseSessionMessagesResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **getSessions**
> ServiceResponseListChatSessionResponse getSessions()


### Example

```typescript
import {
    AIApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

const { status, data } = await apiInstance.getSessions();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**ServiceResponseListChatSessionResponse**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **replyToSession**
> Array<any> replyToSession()


### Example

```typescript
import {
    AIApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

let sessionId: string; // (default to undefined)

const { status, data } = await apiInstance.replyToSession(
    sessionId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **sessionId** | [**string**] |  | defaults to undefined|


### Return type

**Array<any>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/event-stream


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **sendMessage**
> Array<any> sendMessage(chatMessageRequest)


### Example

```typescript
import {
    AIApi,
    Configuration,
    ChatMessageRequest
} from './api';

const configuration = new Configuration();
const apiInstance = new AIApi(configuration);

let sessionId: string; // (default to undefined)
let chatMessageRequest: ChatMessageRequest; //

const { status, data } = await apiInstance.sendMessage(
    sessionId,
    chatMessageRequest
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **chatMessageRequest** | **ChatMessageRequest**|  | |
| **sessionId** | [**string**] |  | defaults to undefined|


### Return type

**Array<any>**

### Authorization

[bearerAuth](../README.md#bearerAuth)

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: text/event-stream


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

