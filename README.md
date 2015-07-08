## mobile-upload-service

### Purpose
Send Request or upload multipart form data to Server

### Set up
* permissions
```cmd
<uses-permission android:name="android.permission.INTERNET" />
```
* Referencing a library project

    http://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject
    
### Examples
* Send parameter to server
```cmd
UploadRequest request = new UploadRequest(this, "request001", <your server url>);
request.setMethod("POST");
request.addHeader(UploadRequest.contentType, "application/json; charset=UTF-8");
request.addParameter("parameterName1", "123");
request.addParameter("parameterName2", "234");
```
* Upload File
```cmd
UploadRequest request = new UploadRequest(this, "request002", <your server url>);
request.setMethod("POST");
request.addParameter("parameterName1", "123");
request.addParameter("parameterName2", "234");
request.addParameter("content", <your fileName>);
request.addFileToUpload(<your file path>, <your file parameter name>, <your fileName>, "application/x-www-form-urlencoded");
```
