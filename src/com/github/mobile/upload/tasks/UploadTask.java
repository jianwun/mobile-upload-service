package com.github.mobile.upload.tasks;

import android.content.Intent;
import android.os.AsyncTask;

import org.json.JSONException;
import org.json.JSONObject;

import com.github.mobile.upload.model.FileToUpload;
import com.github.mobile.upload.model.NameValue;
import com.github.mobile.upload.model.UploadRequest;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class UploadTask extends AsyncTask<Void, Void, Void> {
	
	private static final String NAMESPACE = "com.tradevan.android.services";
	private static final String ACTION_BROADCAST_SUFFIX = ".broadcast.action";
	public static final String BROADCAST_PROCESS = "process";
	private static final String NEW_LINE = "\r\n";
    private static final String TWO_HYPHENS = "--";
    private static final int BUFFER_SIZE = 4096;
    
    private String uploadId;
	private UploadListener uploadLstener;
	private UploadRequest request;
	
    private static boolean FLAG = true;
    
    public static String getActionBroadcast() {
        return NAMESPACE + ACTION_BROADCAST_SUFFIX;
    }
    
    public interface UploadListener {
        void onCompleted(String uploadId, int serverResponseCode, String serverResponseMessage);
        void onProgress(String uploadId, int progress);
        void onError(String uploadId, Exception exception);
    }
    
    public UploadTask(UploadListener listener, UploadRequest request) {
    	this.uploadLstener = listener;
    	this.request = request;
    	uploadId = request.getUploadId();
    }

    @Override
    protected Void doInBackground(Void... params) {
    	try {
    		if( request != null ) {
    			
    			if ( request.getFilesToUpload().size() > 0 ) {
    				sendFileUpload();
    			} else if (request.getParameters().size() > 0) {
    				if( request.getMethod().equals("POST") ) {
    					sendDataRequestByPOST();
    				} else if( request.getMethod().equals("GET") ) {
    					sendDataRequestByGET();
    				}
    			} else {
    				sendRequest();
    			}
    		}
    	} catch (Exception e) {
    		uploadLstener.onError(uploadId, e);
    	}
    	return null;
    }

	/**
	 * File Upload
	 * @param action
	 * @param jsonParam
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public String sendFileUpload() throws IOException {
		
		FLAG = true;
		
		final String boundary = getBoundary();
		final byte[] boundaryBytes = getBoundaryBytes(boundary);
        
		HttpURLConnection urlConnection = null;
		OutputStream requestStream = null;
        InputStream responseStream = null;
        String result = null;
        
		try {
			urlConnection = getMultipartHttpURLConnection(request.getServerUrl(), boundary);
			
			requestStream = urlConnection.getOutputStream();
			
			setRequestParameters(requestStream, request.getParameters(), boundaryBytes);
            
			if( request.getFilesToUpload().size() > 0 ) {
				for( FileToUpload file: request.getFilesToUpload() ) {
					uploadFiles(requestStream, file, boundaryBytes);
				}
			}
            
            if( FLAG ) {
            	final byte[] trailer = getTrailerBytes(boundary);
                requestStream.write(trailer, 0, trailer.length);
                
                final int serverResponseCode = urlConnection.getResponseCode();
                if (serverResponseCode / 100 == 2) {
                    responseStream = urlConnection.getInputStream();
                } else { // getErrorStream if the response code is not 2xx
                    responseStream = urlConnection.getErrorStream();
                }
                result = getResponseBodyAsString(responseStream);
                uploadLstener.onCompleted(uploadId, serverResponseCode, result);
            } else {
            	throw new Exception("user cancel");
            }
		} catch(Exception e) {
			if( e instanceof EOFException ) {
//				uploadLstener.onCompleted(uploadId, -1, "EOFException\n");
				sendFileUpload();
			}
			uploadLstener.onError(uploadId, e);
		} finally {
			closeOutputStream(requestStream);
            closeInputStream(responseStream);
            closeConnection(urlConnection);
		}
		return result;
	}
	
	/**
	 * Send POST Request
	 * @throws IOException
	 * @throws JSONException
	 */
	private void sendDataRequestByPOST() throws IOException, JSONException {
		
		HttpURLConnection urlConnection = getHttpURLConnection(request.getServerUrl());
		
		final String boundary = getBoundary();
		final byte[] boundaryBytes = getBoundaryBytes(boundary);
		
		try {
			InputStream responseStream = null;
			OutputStream outputStream = urlConnection.getOutputStream();
			BufferedOutputStream buffered = new BufferedOutputStream(outputStream);
			
			if( urlConnection.getRequestProperty("Content-Type") != null ) {
				if( urlConnection.getRequestProperty("Content-Type").contains("x-www-form-urlencoded") ) {
					byte[] paramsBytes = request.getFormData().getBytes("utf-8");
					buffered.write(paramsBytes, 0, paramsBytes.length);
					buffered.flush();
					buffered.close();
					outputStream.close();
				} else if( urlConnection.getRequestProperty("Content-Type").contains("form-data") ) {
					setRequestParameters(outputStream, request.getParameters(), boundaryBytes);
					final byte[] trailer = getTrailerBytes(boundary);
					outputStream.write(trailer, 0, trailer.length);
				} else if( urlConnection.getRequestProperty("Content-Type").contains("json") ) {
					byte[] paramsBytes = request.getJsonData().getBytes("utf-8");
					buffered.write(paramsBytes, 0, paramsBytes.length);
					buffered.flush();
					buffered.close();
					outputStream.close();
				}
			}
			
	        final int serverResponseCode = urlConnection.getResponseCode();
	        if (serverResponseCode / 100 == 2) {
	        	responseStream = urlConnection.getInputStream();
	        } else { // getErrorStream if the response code is not 2xx
	        	responseStream = urlConnection.getErrorStream();
	        }
	        
	        uploadLstener.onCompleted(uploadId, serverResponseCode, getResponseBodyAsString(responseStream));
	        responseStream.close();
		} finally {
			urlConnection.disconnect();
		}
	}
	
	/**
	 * Send GET Request
	 * @throws IOException
	 * @throws JSONException
	 */
	private void sendDataRequestByGET() throws IOException {
		StringBuffer url = new StringBuffer();
		url.append(request.getServerUrl()).append("?").append(request.getFormData());
		HttpURLConnection urlConnection = getHttpURLConnection2(url.toString());
		try {
			InputStream responseStream = null;			
	        final int serverResponseCode = urlConnection.getResponseCode();
	        if (serverResponseCode / 100 == 2) {
	        	responseStream = urlConnection.getInputStream();
	        } else { // getErrorStream if the response code is not 2xx
	        	responseStream = urlConnection.getErrorStream();
	        }
	        
	        uploadLstener.onCompleted(uploadId, serverResponseCode, getResponseBodyAsString(responseStream));
	        responseStream.close();
		} finally {
			urlConnection.disconnect();
		}
	}
	
	/**
	 * Send Request No Parameter
	 * @throws IOException 
	 */
	private void sendRequest() throws IOException {
		HttpURLConnection urlConnection = getHttpURLConnection2(request.getServerUrl());
		
		try {
			InputStream responseStream = null;
	        final int serverResponseCode = urlConnection.getResponseCode();
	        if (serverResponseCode / 100 == 2) {
	        	responseStream = urlConnection.getInputStream();
	        } else { // getErrorStream if the response code is not 2xx
	        	responseStream = urlConnection.getErrorStream();
	        }
	        
	        uploadLstener.onCompleted(uploadId, serverResponseCode, getResponseBodyAsString(responseStream));
	        responseStream.close();
		} finally {
			urlConnection.disconnect();
		}
	}

	/**
	 * POST Request Parameter(New)
	 * @param action
	 * @param jsonParam
	 * @return
	 * @throws IOException
	 */
	public String sendRequestParam(String url, JSONObject jsonParam) throws IOException {
		
		HttpURLConnection urlConnection = getHttpURLConnection(url);
		String result = null;

		try {
			InputStream responseStream = null;
			OutputStream outputStream = urlConnection.getOutputStream();
			BufferedOutputStream buffered = new BufferedOutputStream(outputStream);
			
			byte[] paramsBytes = jsonParam.toString().getBytes("UTF-8");
			buffered.write(paramsBytes, 0, paramsBytes.length);
			buffered.flush();
			buffered.close();
			outputStream.close();
			
	        final int serverResponseCode = urlConnection.getResponseCode();
	        if (serverResponseCode / 100 == 2) {
	        	responseStream = urlConnection.getInputStream();
	        } else { // getErrorStream if the response code is not 2xx
	        	responseStream = urlConnection.getErrorStream();
	        }
	        
	        result = getResponseBodyAsString(responseStream);
	        responseStream.close();
		} finally {
			urlConnection.disconnect();
		}
		
        return result;
	}
	
	/**
	 * Post Param Header
	 * @param url
	 * @param boundary
	 * @return
	 * @throws IOException
	 */
	private HttpURLConnection getHttpURLConnection(final String url) throws IOException {
		
		final HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();

		urlConnection.setDoInput(true);
		// upload
		urlConnection.setDoOutput(true);
		// when the body length is known setFixedLengthStreamingMode(int)
		// when the body length is not known setChunkedStreamingMode(int)
//		urlConnection.setChunkedStreamingMode(0);
		urlConnection.setUseCaches(false);
        urlConnection.setRequestMethod(request.getMethod());
        
        for( NameValue nv:request.getHeaders() ) {
        	if( nv.getName().contains(UploadRequest.contentType) && nv.getValue().contains("form-data") ) {
                urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + getBoundary());
        	} else {
        		urlConnection.setRequestProperty(nv.getName(), nv.getValue());
        	}
        }
        // EOFException
        // Appearently this is a bug in newer versions of android.
//        urlConnection.setRequestProperty("Accept-Encoding", "identity");
        urlConnection.setRequestProperty("Accept-Encoding", "");
        urlConnection.setReadTimeout(10000);
        urlConnection.setConnectTimeout(15000);
        
        return urlConnection;
	}
	
	/**
	 * No Param Header
	 * @param url
	 * @param boundary
	 * @return
	 * @throws IOException
	 */
	private HttpURLConnection getHttpURLConnection2(final String url) throws IOException {
		
		final HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();

		urlConnection.setDoInput(true);
		// when the body length is known setFixedLengthStreamingMode(int)
		// when the body length is not known setChunkedStreamingMode(int)
//		urlConnection.setChunkedStreamingMode(0);
		urlConnection.setUseCaches(false);
        urlConnection.setRequestMethod(request.getMethod());
        for( NameValue nv:request.getHeaders() ) {
        	urlConnection.setRequestProperty(nv.getName(), nv.getValue());
        }
        // EOFException
        // Appearently this is a bug in newer versions of android.
//        urlConnection.setRequestProperty("Accept-Encoding", "identity");
        urlConnection.setRequestProperty("Accept-Encoding", "");
        urlConnection.setReadTimeout(10000);
        urlConnection.setConnectTimeout(15000);
        
        return urlConnection;
	}
	
	private String getResponseBodyAsString(final InputStream inputStream) throws IOException {
		StringBuilder outputString = new StringBuilder();
		
		BufferedReader reader = null;
		
		try {
			reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = reader.readLine()) != null) {
				outputString.append(line);
			}
		} finally {
			if (reader != null)
				reader.close();
		}
		return outputString.toString();
	}
	
	/**
	 * Update File Header
	 * @param url
	 * @param boundary
	 * @return
	 * @throws IOException
	 */
	private HttpURLConnection getMultipartHttpURLConnection(final String url, final String boundary) throws IOException {
		final HttpURLConnection urlConnection = (HttpURLConnection) new URL(url).openConnection();

		urlConnection.setDoInput(true);
		urlConnection.setDoOutput(true);
		urlConnection.setUseCaches(false);
//        urlConnection.setChunkedStreamingMode(0);
        urlConnection.setRequestMethod("POST");
        urlConnection.setRequestProperty("Connection", "close");
        urlConnection.setRequestProperty("ENCTYPE", "multipart/form-data");
        urlConnection.setRequestProperty("Charset", "UTF-8");
        urlConnection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        urlConnection.setReadTimeout(15000);
        urlConnection.setConnectTimeout(15000);

        return urlConnection;
	}
	
	private String getBoundary() {
        final StringBuilder builder = new StringBuilder();
        builder.append("---------------------------").append(System.currentTimeMillis());
        return builder.toString();
    }
	
	private byte[] getBoundaryBytes(final String boundary) throws UnsupportedEncodingException {
        final StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE).append(TWO_HYPHENS).append(boundary).append(NEW_LINE);
        return builder.toString().getBytes("US-ASCII");
    }
	
	private byte[] getTrailerBytes(final String boundary) throws UnsupportedEncodingException {
		final StringBuilder builder = new StringBuilder();
		builder.append(NEW_LINE).append(TWO_HYPHENS).append(boundary).append(TWO_HYPHENS).append(NEW_LINE);
		return builder.toString().getBytes("US-ASCII");
    }
	
	private void uploadFiles(final OutputStream requestStream, final FileToUpload filesToUpload, byte[] boundaryBytes)
			throws UnsupportedEncodingException, IOException, FileNotFoundException {

		requestStream.write(boundaryBytes, 0, boundaryBytes.length);
	    byte[] headerBytes = filesToUpload.getMultipartHeader();
	    requestStream.write(headerBytes, 0, headerBytes.length);
	    
	    final InputStream stream = filesToUpload.getStream();
	    byte[] buffer = new byte[BUFFER_SIZE];
	    int bytesRead;
	    int total = 0;
	    long fileSize = filesToUpload.length();
	    try {
	        while ((bytesRead = stream.read(buffer, 0, buffer.length)) > 0) {
	        	
	        	if( FLAG ) {
	        		requestStream.write(buffer, 0, bytesRead);
	 	            
	 	            total = total + bytesRead;
	 	        	if( fileSize > 0 ) {
	 	        		sendBroadcast( ((int) (total * 100 / fileSize)) );
	 				}
	        	} else {
	        		break;
	        	}
	           
	        }
	    } finally {
	        closeInputStream(stream);
	    }
	}
	
	private void setRequestParameters(OutputStream requestStream, ArrayList<NameValue> parameters, byte[] boundaryBytes) throws IOException {
		if (!parameters.isEmpty()) {
            for (final NameValue parameter : parameters) {
                requestStream.write(boundaryBytes, 0, boundaryBytes.length);
                byte[] formItemBytes = parameter.getBytes();
                requestStream.write(formItemBytes, 0, formItemBytes.length);
            }
        }
	}
	
	private void closeInputStream(final InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (Exception exc) {
            }
        }
    }

    private void closeOutputStream(final OutputStream stream) {
        if (stream != null) {
            try {
                stream.flush();
                stream.close();
            } catch (Exception exc) {
            }
        }
    }

    private void closeConnection(final HttpURLConnection connection) {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception exc) {
            }
        }
    }
    
    private void sendBroadcast(int process) {
    	final Intent intent = new Intent(getActionBroadcast());
    	intent.putExtra(BROADCAST_PROCESS, process);
    	request.getContext().sendBroadcast(intent);
    }

	public boolean isFLAG() {
		return FLAG;
	}

	public void setFLAG(boolean fLAG) {
		FLAG = fLAG;
	}
}
