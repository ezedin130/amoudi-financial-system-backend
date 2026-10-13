import json
import os

def make_url(full_path):
    clean_full = full_path.replace("{{api_prefix}}", "/api/v1")
    if not clean_full.startswith("/"):
        clean_full = "/" + clean_full
    
    raw = "{{base_url}}" + clean_full
    
    if "?" in clean_full:
        path_part, query_part = clean_full.split("?", 1)
        query_items = []
        for pair in query_part.split("&"):
            if "=" in pair:
                k, v = pair.split("=", 1)
                query_items.append({"key": k, "value": v})
            else:
                query_items.append({"key": pair, "value": ""})
    else:
        path_part = clean_full
        query_items = []
    
    segments = [s for s in path_part.lstrip("/").split("/") if s]
    
    url_obj = {
        "raw": raw,
        "host": ["{{base_url}}"],
        "path": segments
    }
    if query_items:
        url_obj["query"] = query_items
    return url_obj

def make_header(key, value):
    return {"key": key, "value": value, "type": "text"}

def make_auth_bearer(token_var):
    return {
        "type": "bearer",
        "bearer": [
            {
                "key": "token",
                "value": "{{" + token_var + "}}",
                "type": "string"
            }
        ]
    }

def make_request(name, method, path, headers=None, body_json=None, form_data=None, auth_var=None, tests=None, prerequest=None, description=""):
    req = {
        "name": name,
        "request": {
            "method": method,
            "header": headers or [],
            "url": make_url(path),
            "description": description
        },
        "response": []
    }
    
    if auth_var:
        req["request"]["auth"] = make_auth_bearer(auth_var)
    
    if body_json is not None:
        req["request"]["header"].append(make_header("Content-Type", "application/json"))
        req["request"]["body"] = {
            "mode": "raw",
            "raw": json.dumps(body_json, indent=2),
            "options": {
                "raw": {
                    "language": "json"
                }
            }
        }
    elif form_data is not None:
        req["request"]["body"] = {
            "mode": "formdata",
            "formdata": form_data
        }
    
    events = []
    if prerequest:
        events.append({
            "listen": "prerequest",
            "script": {
                "exec": prerequest,
                "type": "text/javascript"
            }
        })
    if tests:
        events.append({
            "listen": "test",
            "script": {
                "exec": tests,
                "type": "text/javascript"
            }
        })
    if events:
        req["event"] = events
    
    return req

def build_collection():
    collection = {
        "info": {
            "_postman_id": "7b0a88bf-9831-4ce8-bce6-31ec3094db22",
            "name": "AMOUDI FINANCIAL MANAGEMENT API",
            "description": "# Amoudi Financial Management API Collection\n\nProduction-quality Postman collection covering all currently implemented backend modules:\n- **00 - Health**: System readiness and PostgreSQL status check\n- **01 - Authentication**: JWT authentication (Finance and General Manager roles) and profile inspection\n- **02 - Non-IATA Management**: CRUD, status transitions, search, pagination, and financial summaries\n- **03 - Sales**: Sales transactions query, filtering, and immutability checks (POST/PUT/DELETE return 405)\n- **04 - Imports**: Galileo/BSP Excel sales report upload (Sheet2 authoritative), batch listing, and name mappings\n- **05 - Financial Summaries**: Bulk and single Non-IATA summaries demonstrating the derived formula:\n  `OUTSTANDING BALANCE = TOTAL SALES - TOTAL POSTED PAYMENTS`\n- **06 - Payments**: Payment posting, receipt upload/retrieval, and payment cancellation\n- **07 - Statements**: Account statements with chronological ledger, Excel export, and PDF export\n- **08 - Aging Reports**: Dynamic accounts receivable aging buckets (Current, 31-60, 61-90, 91-120, 120+), Excel, and PDF export\n- **09 - Audit Logs**: Immutable audit trail listing and filtering (Finance only)\n- **10 - Security & RBAC Tests**: 401 unauthenticated, 403 role boundary violations, and 400 validation failures\n- **11 - End-to-End Financial Lifecycle**: Full transaction lifecycle from login to reconciliation\n\n### Authentication Roles\n1. `FINANCE` (e.g. `finance_user`): Manages Non-IATAs, uploads Galileo/BSP reports, cancels payments, accesses audit logs.\n2. `GENERAL_MANAGER` (e.g. `admin`): Posts payments, uploads payment receipts, views reports and statements.\n\n### Standard Error Response Structure\nAll errors return unified JSON matching `ErrorResponse`:\n```json\n{\n  \"timestamp\": \"2026-09-30T13:00:00Z\",\n  \"status\": 400,\n  \"error\": \"VALIDATION_ERROR\",\n  \"message\": \"Parameter validation failed\",\n  \"path\": \"/api/v1/payments\",\n  \"correlationId\": \"c0a80101-...\",\n  \"details\": [\"Payment amount must be greater than zero\"]\n}\n```",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "item": []
    }

    # =========================================================================
    # 00 - Health
    # =========================================================================
    folder_health = {
        "name": "00 - Health",
        "description": "System readiness, database connectivity, and environment diagnosis.",
        "item": [
            make_request(
                name="Check System Health",
                method="GET",
                path="{{api_prefix}}/health",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var json = pm.response.json();",
                    "pm.test('System status is UP', function () {",
                    "    pm.expect(json.status).to.eql('UP');",
                    "    pm.expect(json.database).to.include('CONNECTED');",
                    "    pm.expect(json.application).to.include('Amoudi Financial');",
                    "});"
                ],
                description="Endpoint: GET /api/v1/health\nPublic endpoint returning system status and PostgreSQL connectivity without requiring authentication."
            )
        ]
    }

    # =========================================================================
    # 01 - Authentication
    # =========================================================================
    folder_auth = {
        "name": "01 - Authentication",
        "description": "JWT-based authentication endpoints. Captures accessToken and stores it in environment variables.",
        "item": [
            make_request(
                name="Login as Finance",
                method="POST",
                path="{{api_prefix}}/auth/login",
                body_json={
                    "username": "{{finance_username}}",
                    "password": "{{finance_password}}"
                },
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Finance login successful with JWT', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.accessToken).to.not.be.empty;",
                    "    pm.expect(res.data.user.role).to.eql('FINANCE');",
                    "    pm.environment.set('finance_token', res.data.accessToken);",
                    "    console.log('Finance token set successfully');",
                    "});"
                ],
                description="Endpoint: POST /api/v1/auth/login\nAuthenticates Finance credentials and saves JWT into {{finance_token}}."
            ),
            make_request(
                name="Login as General Manager",
                method="POST",
                path="{{api_prefix}}/auth/login",
                body_json={
                    "username": "{{gm_username}}",
                    "password": "{{gm_password}}"
                },
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('General Manager login successful with JWT', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.accessToken).to.not.be.empty;",
                    "    pm.expect(res.data.user.role).to.eql('GENERAL_MANAGER');",
                    "    pm.environment.set('gm_token', res.data.accessToken);",
                    "    console.log('GM token set successfully');",
                    "});"
                ],
                description="Endpoint: POST /api/v1/auth/login\nAuthenticates General Manager credentials and saves JWT into {{gm_token}}."
            ),
            make_request(
                name="Get Current User Profile (Finance)",
                method="GET",
                path="{{api_prefix}}/auth/me",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Profile matches Finance role', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.role).to.eql('FINANCE');",
                    "    pm.expect(res.data.status).to.eql('ACTIVE');",
                    "});"
                ],
                description="Endpoint: GET /api/v1/auth/me\nRetrieves the safe user profile of the authenticated Finance user."
            ),
            make_request(
                name="Get Current User Profile (General Manager)",
                method="GET",
                path="{{api_prefix}}/auth/me",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Profile matches General Manager role', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.role).to.eql('GENERAL_MANAGER');",
                    "    pm.expect(res.data.status).to.eql('ACTIVE');",
                    "});"
                ],
                description="Endpoint: GET /api/v1/auth/me\nRetrieves the safe user profile of the authenticated General Manager."
            )
        ]
    }

    # =========================================================================
    # 02 - Non-IATA Management
    # =========================================================================
    folder_non_iatas = {
        "name": "02 - Non-IATA Management",
        "description": "Non-IATA sub-agent account management. Write operations restricted to FINANCE.",
        "item": [
            make_request(
                name="Create Non-IATA Account",
                method="POST",
                path="{{api_prefix}}/non-iatas",
                auth_var="finance_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('unique_code', 'PM_' + suffix);",
                    "pm.environment.set('unique_name', 'Postman Verified Agency ' + suffix);",
                    "pm.environment.set('unique_email', 'agency_' + suffix + '@amoudi.com');"
                ],
                body_json={
                    "code": "{{unique_code}}",
                    "name": "{{unique_name}}",
                    "contactName": "Abebe Tester",
                    "phone": "+251911000000",
                    "email": "{{unique_email}}",
                    "notes": "Created by Postman API Suite"
                },
                tests=[
                    "pm.test('Status code is 201 Created or 200 OK', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                    "});",
                    "var res = pm.response.json();",
                    "if (res.data && res.data.id) {",
                    "    pm.environment.set('non_iata_id', res.data.id);",
                    "    pm.environment.set('non_iata_code', res.data.code);",
                    "    console.log('Saved non_iata_id: ' + res.data.id);",
                    "}"
                ],
                description="Endpoint: POST /api/v1/non-iatas\nCreates a new Non-IATA sub-agent account. Restricted to FINANCE role. Saves ID to {{non_iata_id}}."
            ),
            make_request(
                name="List Non-IATA Accounts",
                method="GET",
                path="{{api_prefix}}/non-iatas?page=0&size=20&sort=createdAt,desc",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Paginated list contains content', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.content).to.be.an('array');",
                    "    pm.expect(res.data.totalElements).to.be.at.least(0);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas\nRetrieves paginated Non-IATA accounts. Accessible by FINANCE and GENERAL_MANAGER."
            ),
            make_request(
                name="List Non-IATA Accounts - Filtered",
                method="GET",
                path="{{api_prefix}}/non-iatas?search=Postman&status=ACTIVE",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Response is valid page', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.content).to.be.an('array');",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas?search=...&status=...\nFilters Non-IATAs by search keyword and status. Tested with GM token."
            ),
            make_request(
                name="Get Non-IATA Details by ID",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Non-IATA details retrieved', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.id).to.eql(Number(pm.environment.get('non_iata_id')));",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{id}\nRetrieves single Non-IATA details. Accessible by FINANCE and GENERAL_MANAGER."
            ),
            make_request(
                name="Update Non-IATA Account",
                method="PUT",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}",
                auth_var="finance_token",
                body_json={
                    "name": "Postman Verified Agency (Updated)",
                    "contactName": "Abebe Senior Tester",
                    "phone": "+251911999888",
                    "email": "postman.updated@amoudi.com",
                    "notes": "Updated contact info via Postman"
                },
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Non-IATA name updated', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.name).to.eql('Postman Verified Agency (Updated)');",
                    "});"
                ],
                description="Endpoint: PUT /api/v1/non-iatas/{id}\nUpdates editable fields (code is immutable). Restricted to FINANCE role."
            ),
            make_request(
                name="Update Non-IATA Status (Deactivate)",
                method="PATCH",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/status",
                auth_var="finance_token",
                body_json={
                    "status": "INACTIVE"
                },
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Status set to INACTIVE', function () {",
                    "    pm.expect(res.data.status).to.eql('INACTIVE');",
                    "});"
                ],
                description="Endpoint: PATCH /api/v1/non-iatas/{id}/status\nSets operational status to INACTIVE. Restricted to FINANCE role."
            ),
            make_request(
                name="Update Non-IATA Status (Reactivate)",
                method="PATCH",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/status",
                auth_var="finance_token",
                body_json={
                    "status": "ACTIVE"
                },
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Status restored to ACTIVE', function () {",
                    "    pm.expect(res.data.status).to.eql('ACTIVE');",
                    "});"
                ],
                description="Endpoint: PATCH /api/v1/non-iatas/{id}/status\nRestores operational status to ACTIVE. Restricted to FINANCE role."
            ),
            make_request(
                name="Get Non-IATA Financial Summary",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/financial-summary",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Financial summary invariant holds', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    var sales = Number(res.data.totalSales || 0);",
                    "    var payments = Number(res.data.totalPayments || 0);",
                    "    var balance = Number(res.data.outstandingBalance || 0);",
                    "    var expected = Number((sales - payments).toFixed(2));",
                    "    pm.expect(balance).to.eql(expected);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{id}/financial-summary\nReturns totalSales, totalPayments, and outstandingBalance. Verifies core invariant."
            )
        ]
    }

    # =========================================================================
    # 03 - Sales
    # =========================================================================
    folder_sales = {
        "name": "03 - Sales",
        "description": "Read-only access to ingested sales transactions. POST/PUT/DELETE return 405 Method Not Allowed.",
        "item": [
            make_request(
                name="List Sales Transactions",
                method="GET",
                path="{{api_prefix}}/sales?page=0&size=20&sort=saleDate,desc",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Sales list returns PageResponse', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.content).to.be.an('array');",
                    "    if (res.data.content.length > 0) {",
                    "        pm.environment.set('sales_id', res.data.content[0].id);",
                    "        console.log('Saved sales_id: ' + res.data.content[0].id);",
                    "    }",
                    "});"
                ],
                description="Endpoint: GET /api/v1/sales\nLists paginated ticket sales. Accessible by FINANCE and GENERAL_MANAGER."
            ),
            make_request(
                name="List Sales Transactions - Filtered by Non-IATA",
                method="GET",
                path="{{api_prefix}}/sales?nonIataId={{non_iata_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/sales?nonIataId=...\nFilters sales transactions by Non-IATA account."
            ),
            make_request(
                name="List Sales Transactions - Filtered by Date Range",
                method="GET",
                path="{{api_prefix}}/sales?startDate={{statement_date_from}}&endDate={{statement_date_to}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/sales?startDate=...&endDate=...\nFilters sales by transaction saleDate range."
            ),
            make_request(
                name="Get Sales Transaction Details by ID",
                method="GET",
                path="{{api_prefix}}/sales/{{sales_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK or 404', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 404]);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/sales/{id}\nRetrieves single ticket transaction details."
            ),
            make_request(
                name="Immutability Check: POST Sales Disallowed (405)",
                method="POST",
                path="{{api_prefix}}/sales",
                auth_var="finance_token",
                body_json={"amount": 5000.00},
                tests=[
                    "pm.test('Status code is 405 Method Not Allowed', function () {",
                    "    pm.response.to.have.status(405);",
                    "});"
                ],
                description="Proves sales write operations are prohibited. Returns HTTP 405 Method Not Allowed."
            ),
            make_request(
                name="Immutability Check: PUT Sales Disallowed (405)",
                method="PUT",
                path="{{api_prefix}}/sales/{{sales_id}}",
                auth_var="finance_token",
                body_json={"amount": 5000.00},
                tests=[
                    "pm.test('Status code is 405 Method Not Allowed', function () {",
                    "    pm.response.to.have.status(405);",
                    "});"
                ],
                description="Proves modifying sales transactions is prohibited. Returns HTTP 405 Method Not Allowed."
            ),
            make_request(
                name="Immutability Check: DELETE Sales Disallowed (405)",
                method="DELETE",
                path="{{api_prefix}}/sales/{{sales_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 405 Method Not Allowed', function () {",
                    "    pm.response.to.have.status(405);",
                    "});"
                ],
                description="Proves deleting sales transactions is prohibited. Returns HTTP 405 Method Not Allowed."
            )
        ]
    }

    # =========================================================================
    # 04 - Imports
    # =========================================================================
    folder_imports = {
        "name": "04 - Imports",
        "description": "Galileo/BSP daily sales report Excel import (Sheet2 authoritative) and name mapping management.",
        "item": [
            make_request(
                name="Upload Galileo BSP Sales Report",
                method="POST",
                path="{{api_prefix}}/imports",
                auth_var="finance_token",
                form_data=[
                    {
                        "key": "file",
                        "type": "file",
                        "src": "{{excel_file}}",
                        "description": "Select Galileo/BSP Excel file: SEP- 17.xlsx"
                    }
                ],
                tests=[
                    "pm.test('Status code is 201 Created or 200 OK', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                    "});",
                    "var res = pm.response.json();",
                    "if (res.data && res.data.id) {",
                    "    pm.environment.set('import_batch_id', res.data.id);",
                    "    console.log('Saved import_batch_id: ' + res.data.id);",
                    "}"
                ],
                description="Endpoint: POST /api/v1/imports\nUploads Galileo/BSP report (multipart/form-data: file). Restricted to FINANCE."
            ),
            make_request(
                name="List Import Batches",
                method="GET",
                path="{{api_prefix}}/imports?page=0&size=20&sort=createdAt,desc",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/imports\nLists historical import batches and statuses (IMPORTED, PARTIALLY_IMPORTED, FAILED)."
            ),
            make_request(
                name="Get Import Batch Details by ID",
                method="GET",
                path="{{api_prefix}}/imports/{{import_batch_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/imports/{id}\nRetrieves batch metrics: totalRows, successfulRows, duplicateRows, unmatchedRows."
            ),
            make_request(
                name="Get Import Batch Rejection Errors",
                method="GET",
                path="{{api_prefix}}/imports/{{import_batch_id}}/errors?page=0&size=50",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/imports/{id}/errors\nRetrieves row-level rejection details (e.g. UNMATCHED_AGENCY, DUPLICATE_TRANSACTION)."
            ),
            make_request(
                name="List Name Mappings",
                method="GET",
                path="{{api_prefix}}/name-mappings?page=0&size=50",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/name-mappings\nLists alias mappings translating source agency strings in Excel to canonical Non-IATAs."
            ),
            make_request(
                name="Create Name Mapping",
                method="POST",
                path="{{api_prefix}}/name-mappings",
                auth_var="finance_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('unique_source_name', 'PM*TEST AGENCY ' + suffix);"
                ],
                body_json={
                    "sourceName": "{{unique_source_name}}",
                    "nonIataId": "{{non_iata_id}}"
                },
                tests=[
                    "pm.test('Status code is 201 Created or 200 OK or 409 Conflict', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201, 409]);",
                    "});"
                ],
                description="Endpoint: POST /api/v1/name-mappings\nRegisters an authoritative alias rule. Restricted to FINANCE role."
            )
        ]
    }

    # =========================================================================
    # 05 - Financial Summaries
    folder_summaries = {
    # =========================================================================
        "name": "05 - Financial Summaries",
        "description": "Calculates and presents derived financial balances: Outstanding = Total Sales - Total Payments.",
        "item": [
            make_request(
                name="List Bulk Financial Summaries",
                method="GET",
                path="{{api_prefix}}/financial-summaries?page=0&size=20&sort=name,asc",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Core financial invariant holds on all rows', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    var rows = res.data.content || [];",
                    "    rows.forEach(function (row) {",
                    "        var sales = Number(row.totalSales || 0);",
                    "        var payments = Number(row.totalPayments || 0);",
                    "        var balance = Number(row.outstandingBalance || 0);",
                    "        var expected = Number((sales - payments).toFixed(2));",
                    "        pm.expect(balance).to.eql(expected);",
                    "    });",
                    "});"
                ],
                description="Endpoint: GET /api/v1/financial-summaries\nAggregates sales and payments dynamically across all Non-IATA accounts. Validates formula."
            ),
            make_request(
                name="List Financial Summaries - Filtered",
                method="GET",
                path="{{api_prefix}}/financial-summaries?search=Halifa&status=ACTIVE",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/financial-summaries?search=...&status=...\nFiltered financial overview by agency name and operational status."
            ),
            make_request(
                name="Get Single Non-IATA Financial Summary",
                method="GET",
                path="{{api_prefix}}/financial-summaries/{{non_iata_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Single summary matches mathematical invariant', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    var s = Number(res.data.totalSales || 0);",
                    "    var p = Number(res.data.totalPayments || 0);",
                    "    var b = Number(res.data.outstandingBalance || 0);",
                    "    var exp = Number((s - p).toFixed(2));",
                    "    pm.expect(b).to.eql(exp);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/financial-summaries/{nonIataId}\nRetrieves current derived balance and total metrics for specified Non-IATA."
            )
        ]
    }

    # =========================================================================
    # 06 - Payments
    # =========================================================================
    folder_payments = {
        "name": "06 - Payments",
        "description": "Account-level payments management. Creation/receipts restricted to GM; cancellation restricted to FINANCE.",
        "item": [
            make_request(
                name="Create Payment (GM Role)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="gm_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('pm_receipt_num', 'REC-PM-' + suffix);",
                    "pm.environment.set('pm_payment_ref', 'REF-PM-' + suffix);"
                ],
                body_json={
                    "nonIataId": "{{non_iata_id}}",
                    "amount": 15000.00,
                    "currency": "ETB",
                    "paymentDate": "{{current_date}}",
                    "receiptNumber": "{{pm_receipt_num}}",
                    "paymentReference": "{{pm_payment_ref}}",
                    "notes": "Payment created via Postman test suite"
                },
                tests=[
                    "pm.test('Status code is 201 Created', function () {",
                    "    pm.response.to.have.status(201);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Payment created with POSTED status', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.status).to.eql('POSTED');",
                    "    pm.expect(res.data.id).to.be.a('number');",
                    "    pm.environment.set('payment_id', res.data.id);",
                    "    console.log('Saved payment_id: ' + res.data.id);",
                    "});"
                ],
                description="Endpoint: POST /api/v1/payments\nCreates an account-level payment in POSTED status. Restricted to GENERAL_MANAGER role."
            ),
            make_request(
                name="List Payments",
                method="GET",
                path="{{api_prefix}}/payments?page=0&size=20&sort=paymentDate,desc",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Payments list returned', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.content).to.be.an('array');",
                    "});"
                ],
                description="Endpoint: GET /api/v1/payments\nLists paginated payment transactions with metadata. Accessible by FINANCE and GM."
            ),
            make_request(
                name="List Payments - Filtered by Non-IATA & Status",
                method="GET",
                path="{{api_prefix}}/payments?nonIataId={{non_iata_id}}&status=POSTED",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/payments?nonIataId=...&status=...\nFilters payment transactions by account and status."
            ),
            make_request(
                name="Get Payment Details by ID",
                method="GET",
                path="{{api_prefix}}/payments/{{payment_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Payment details match ID', function () {",
                    "    pm.expect(res.data.id).to.eql(Number(pm.environment.get('payment_id')));",
                    "});"
                ],
                description="Endpoint: GET /api/v1/payments/{id}\nRetrieves full payment details and receipt attachment metadata."
            ),
            make_request(
                name="Upload Payment Receipt (GM Role)",
                method="POST",
                path="{{api_prefix}}/payments/{{payment_id}}/receipt",
                auth_var="gm_token",
                form_data=[
                    {
                        "key": "file",
                        "type": "file",
                        "src": "{{receipt_file}}",
                        "description": "Select receipt document (PDF, PNG, JPG)"
                    }
                ],
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Receipt attached successfully', function () {",
                    "    pm.expect(res.data.hasReceipt).to.be.true;",
                    "    pm.expect(res.data.receiptObjectKey).to.not.be.empty;",
                    "});"
                ],
                description="Endpoint: POST /api/v1/payments/{id}/receipt\nUploads receipt file to date-partitioned storage. Restricted to GENERAL_MANAGER."
            ),
            make_request(
                name="Retrieve Payment Receipt Binary File",
                method="GET",
                path="{{api_prefix}}/payments/{{payment_id}}/receipt",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Content-Disposition header present', function () {",
                    "        var cd = pm.response.headers.get('Content-Disposition') || '';",
                    "        pm.expect(cd).to.include('filename=');",
                    "    });",
                    "}"
                ],
                description="Endpoint: GET /api/v1/payments/{id}/receipt\nStreams binary receipt file. Accessible by FINANCE and GENERAL_MANAGER."
            ),
            make_request(
                name="Cancel Payment (Finance Role)",
                method="POST",
                path="{{api_prefix}}/payments/{{payment_id}}/cancel",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Payment status is CANCELLED', function () {",
                    "    pm.expect(res.data.status).to.eql('CANCELLED');",
                    "});"
                ],
                description="Endpoint: POST /api/v1/payments/{id}/cancel\nCancels payment and reverses credit from balance. Restricted to FINANCE role."
            )
        ]
    }

    # =========================================================================
    # 07 - Statements
    # =========================================================================
    folder_statements = {
        "name": "07 - Statements",
        "description": "Chronological ledger statement of account with running balance, Excel, and PDF exports.",
        "item": [
            make_request(
                name="Get Account Statement (Full History)",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/statement",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Statement math reconciles perfectly', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    var openBal = Number(res.data.openingBalance || 0);",
                    "    var sales = Number(res.data.totalSales || 0);",
                    "    var payments = Number(res.data.totalPayments || 0);",
                    "    var closeBal = Number(res.data.closingBalance || 0);",
                    "    var expected = Number((openBal + sales - payments).toFixed(2));",
                    "    pm.expect(closeBal).to.eql(expected);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{nonIataId}/statement\nReturns openingBalance, totalSales, totalPayments, closingBalance, and chronological transaction ledger."
            ),
            make_request(
                name="Get Account Statement - Date Filtered",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/statement?dateFrom={{statement_date_from}}&dateTo={{statement_date_to}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{nonIataId}/statement?dateFrom=...&dateTo=...\nRetrieves statement scoped to specific date range."
            ),
            make_request(
                name="Export Account Statement - Excel",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/statement/export/excel?dateFrom={{statement_date_from}}&dateTo={{statement_date_to}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Content-Type is Excel spreadsheet', function () {",
                    "        var ct = pm.response.headers.get('Content-Type') || '';",
                    "        pm.expect(ct).to.include('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');",
                    "    });",
                    "    pm.test('Content-Disposition is attachment', function () {",
                    "        var cd = pm.response.headers.get('Content-Disposition') || '';",
                    "        pm.expect(cd).to.include('attachment; filename=');",
                    "    });",
                    "    pm.test('Excel response is non-empty binary', function () {",
                    "        pm.expect(pm.response.responseSize).to.be.above(0);",
                    "    });",
                    "}"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{nonIataId}/statement/export/excel\nExports formatted statement spreadsheet using Apache POI."
            ),
            make_request(
                name="Export Account Statement - PDF",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{non_iata_id}}/statement/export/pdf?dateFrom={{statement_date_from}}&dateTo={{statement_date_to}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Content-Type is PDF', function () {",
                    "        var ct = pm.response.headers.get('Content-Type') || '';",
                    "        pm.expect(ct).to.include('application/pdf');",
                    "    });",
                    "    pm.test('PDF response is non-empty binary', function () {",
                    "        pm.expect(pm.response.responseSize).to.be.above(0);",
                    "    });",
                    "}"
                ],
                description="Endpoint: GET /api/v1/non-iatas/{nonIataId}/statement/export/pdf\nExports printable PDF document using OpenPDF."
            )
        ]
    }

    # =========================================================================
    # 08 - Aging Reports
    # =========================================================================
    folder_aging = {
        "name": "08 - Aging Reports",
        "description": "Accounts Receivable aging reports across buckets (Current, 31-60, 61-90, 91-120, 120+) with Excel & PDF exports.",
        "item": [
            make_request(
                name="Get Aging Report (Current)",
                method="GET",
                path="{{api_prefix}}/aging",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Aging buckets sum to total outstanding', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    var d = res.data;",
                    "    var b1 = Number(d.totalCurrent || 0);",
                    "    var b2 = Number(d.totalDays31To60 || 0);",
                    "    var b3 = Number(d.totalDays61To90 || 0);",
                    "    var b4 = Number(d.totalDays91To120 || 0);",
                    "    var b5 = Number(d.totalDays120Plus || 0);",
                    "    var tot = Number(d.totalOutstanding || 0);",
                    "    var exp = Number((b1 + b2 + b3 + b4 + b5).toFixed(2));",
                    "    pm.expect(tot).to.eql(exp);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/aging\nAggregates outstanding receivables across standard aging buckets as of current date."
            ),
            make_request(
                name="Get Aging Report - Filtered",
                method="GET",
                path="{{api_prefix}}/aging?asOfDate={{aging_as_of_date}}&nonIataId={{non_iata_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});"
                ],
                description="Endpoint: GET /api/v1/aging?asOfDate=...&nonIataId=...\nCalculates aging metrics as of a historical date for a specified Non-IATA."
            ),
            make_request(
                name="Export Aging Report - Excel",
                method="GET",
                path="{{api_prefix}}/aging/export/excel?asOfDate={{aging_as_of_date}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Content-Type is Excel spreadsheet', function () {",
                    "        var ct = pm.response.headers.get('Content-Type') || '';",
                    "        pm.expect(ct).to.include('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');",
                    "    });",
                    "    pm.test('Excel response is non-empty binary', function () {",
                    "        pm.expect(pm.response.responseSize).to.be.above(0);",
                    "    });",
                    "}"
                ],
                description="Endpoint: GET /api/v1/aging/export/excel\nExports formatted aging matrix to Excel with Grand Total row."
            ),
            make_request(
                name="Export Aging Report - PDF",
                method="GET",
                path="{{api_prefix}}/aging/export/pdf?asOfDate={{aging_as_of_date}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Content-Type is PDF', function () {",
                    "        var ct = pm.response.headers.get('Content-Type') || '';",
                    "        pm.expect(ct).to.include('application/pdf');",
                    "    });",
                    "    pm.test('PDF response is non-empty binary', function () {",
                    "        pm.expect(pm.response.responseSize).to.be.above(0);",
                    "    });",
                    "}"
                ],
                description="Endpoint: GET /api/v1/aging/export/pdf\nExports multi-column aging report table to landscape PDF."
            )
        ]
    }

    # =========================================================================
    # 09 - Audit Logs
    # =========================================================================
    folder_audit = {
        "name": "09 - Audit Logs",
        "description": "Immutable, append-only security and operational audit trail. Restricted to FINANCE.",
        "item": [
            make_request(
                name="List Audit Logs (Finance Only)",
                method="GET",
                path="{{api_prefix}}/audit-logs?page=0&size=20",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Audit logs retrieved with standard fields', function () {",
                    "    pm.expect(res.success).to.be.true;",
                    "    pm.expect(res.data.content).to.be.an('array');",
                    "    if (res.data.content.length > 0) {",
                    "        var log = res.data.content[0];",
                    "        pm.expect(log.id).to.be.a('number');",
                    "        pm.expect(log.action).to.not.be.empty;",
                    "    }",
                    "});"
                ],
                description="Endpoint: GET /api/v1/audit-logs\nRetrieves paginated audit log entries. Strictly restricted to FINANCE."
            )
        ]
    }

    # =========================================================================
    # 10 - Security & RBAC Tests
    # =========================================================================
    folder_security = {
        "name": "10 - Security & RBAC Tests",
        "description": "Negative testing suite verifying authentication barriers (401), role boundaries (403), and input validations (400).",
        "item": [
            make_request(
                name="Auth Barrier: Missing Bearer Token (401)",
                method="GET",
                path="{{api_prefix}}/non-iatas",
                tests=[
                    "pm.test('Status code is 401 Unauthorized', function () {",
                    "    pm.response.to.have.status(401);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Standard ErrorResponse structure returned', function () {",
                    "    pm.expect(res.status).to.eql(401);",
                    "    pm.expect(res.error).to.eql('UNAUTHORIZED');",
                    "    pm.expect(res.path).to.include('/api/v1/non-iatas');",
                    "});"
                ],
                description="Verifies unauthenticated access is blocked with standard ErrorResponse."
            ),
            make_request(
                name="Auth Barrier: Tampered / Malformed JWT (401)",
                method="GET",
                path="{{api_prefix}}/non-iatas",
                headers=[make_header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.malicious.signature")],
                tests=[
                    "pm.test('Status code is 401 Unauthorized', function () {",
                    "    pm.response.to.have.status(401);",
                    "});"
                ],
                description="Verifies forged or malformed tokens are rejected immediately."
            ),
            make_request(
                name="RBAC Boundary: Finance Attempting to Create Payment (403)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="finance_token",
                body_json={
                    "nonIataId": "{{non_iata_id}}",
                    "amount": 1000.00,
                    "paymentDate": "{{current_date}}"
                },
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Access Denied error message returned', function () {",
                    "    pm.expect(res.status).to.eql(403);",
                    "    pm.expect(res.error).to.eql('ACCESS_DENIED');",
                    "});"
                ],
                description="Payment creation is restricted to GENERAL_MANAGER. FINANCE role must receive HTTP 403."
            ),
            make_request(
                name="RBAC Boundary: Finance Attempting to Upload Receipt (403)",
                method="POST",
                path="{{api_prefix}}/payments/{{payment_id}}/receipt",
                auth_var="finance_token",
                form_data=[
                    {
                        "key": "file",
                        "type": "file",
                        "src": "{{receipt_file}}",
                        "description": "Receipt binary file"
                    }
                ],
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});"
                ],
                description="Receipt attachment is restricted to GENERAL_MANAGER. FINANCE role must receive HTTP 403."
            ),
            make_request(
                name="RBAC Boundary: GM Attempting to Cancel Payment (403)",
                method="POST",
                path="{{api_prefix}}/payments/{{payment_id}}/cancel",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});"
                ],
                description="Payment cancellation is restricted to FINANCE. GENERAL_MANAGER role must receive HTTP 403."
            ),
            make_request(
                name="RBAC Boundary: GM Attempting to Create Non-IATA (403)",
                method="POST",
                path="{{api_prefix}}/non-iatas",
                auth_var="gm_token",
                body_json={
                    "code": "FORBID01",
                    "name": "Forbidden Agency"
                },
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});"
                ],
                description="Non-IATA creation is restricted to FINANCE. GENERAL_MANAGER role must receive HTTP 403."
            ),
            make_request(
                name="RBAC Boundary: GM Attempting to Ingest Sales Report (403)",
                method="POST",
                path="{{api_prefix}}/imports",
                auth_var="gm_token",
                form_data=[
                    {
                        "key": "file",
                        "type": "file",
                        "src": "{{excel_file}}",
                        "description": "Galileo/BSP Excel file"
                    }
                ],
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});"
                ],
                description="Excel report ingestion is restricted to FINANCE. GENERAL_MANAGER role must receive HTTP 403."
            ),
            make_request(
                name="RBAC Boundary: GM Attempting to View Audit Logs (403)",
                method="GET",
                path="{{api_prefix}}/audit-logs",
                auth_var="gm_token",
                tests=[
                    "pm.test('Status code is 403 Forbidden', function () {",
                    "    pm.response.to.have.status(403);",
                    "});"
                ],
                description="Audit logs access is restricted to FINANCE. GENERAL_MANAGER role must receive HTTP 403."
            ),
            make_request(
                name="Validation: Blank Non-IATA Code (400)",
                method="POST",
                path="{{api_prefix}}/non-iatas",
                auth_var="finance_token",
                body_json={
                    "code": "",
                    "name": "Blank Code Agency"
                },
                tests=[
                    "pm.test('Status code is 400 Bad Request', function () {",
                    "    pm.response.to.have.status(400);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Validation details identify blank code', function () {",
                    "    pm.expect(res.details.some(d => d.includes('Code is required') || d.includes('Code must be between'))).to.be.true;",
                    "});"
                ],
                description="Tests Jakarta validation on NonIataCreateRequest."
            ),
            make_request(
                name="Validation: Future Payment Date (400)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="gm_token",
                body_json={
                    "nonIataId": "{{non_iata_id}}",
                    "amount": 5000.00,
                    "paymentDate": "2099-12-31"
                },
                tests=[
                    "pm.test('Status code is 400 Bad Request', function () {",
                    "    pm.response.to.have.status(400);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Validation details reject future date', function () {",
                    "    pm.expect(res.details.some(d => d.includes('future'))).to.be.true;",
                    "});"
                ],
                description="Tests @PastOrPresent validation on CreatePaymentRequest.paymentDate."
            ),
            make_request(
                name="Validation: Negative Payment Amount (400)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="gm_token",
                body_json={
                    "nonIataId": "{{non_iata_id}}",
                    "amount": -50.00,
                    "paymentDate": "{{current_date}}"
                },
                tests=[
                    "pm.test('Status code is 400 Bad Request', function () {",
                    "    pm.response.to.have.status(400);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Validation details reject negative amount', function () {",
                    "    pm.expect(res.details.some(d => d.includes('greater than zero'))).to.be.true;",
                    "});"
                ],
                description="Tests @DecimalMin validation on CreatePaymentRequest.amount."
            ),
            make_request(
                name="Validation: Malformed Email Address (400)",
                method="POST",
                path="{{api_prefix}}/non-iatas",
                auth_var="finance_token",
                body_json={
                    "code": "BAD_EM01",
                    "name": "Invalid Email Agency",
                    "email": "not-a-valid-email-string"
                },
                tests=[
                    "pm.test('Status code is 400 Bad Request', function () {",
                    "    pm.response.to.have.status(400);",
                    "});",
                    "var res = pm.response.json();",
                    "pm.test('Validation details reject malformed email', function () {",
                    "    pm.expect(res.details.some(d => d.includes('Invalid email format'))).to.be.true;",
                    "});"
                ],
                description="Tests @Email validation on NonIataCreateRequest.email."
            )
        ]
    }

    # =========================================================================
    # 11 - End-to-End Financial Lifecycle
    # =========================================================================
    folder_lifecycle = {
        "name": "11 - End-to-End Financial Lifecycle",
        "description": "Multi-step automated transaction sequence testing complete lifecycle: Login -> Account Creation -> Payments -> Statement -> Aging -> Cancellation -> Balance Reversion.",
        "item": [
            make_request(
                name="L-01: Authenticate Finance Officer",
                method="POST",
                path="{{api_prefix}}/auth/login",
                body_json={
                    "username": "{{finance_username}}",
                    "password": "{{finance_password}}"
                },
                tests=[
                    "pm.test('Finance login successful', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var token = pm.response.json().data.accessToken;",
                    "    pm.environment.set('finance_token', token);",
                    "});"
                ],
                description="Step 1: Obtain fresh Finance JWT token."
            ),
            make_request(
                name="L-02: Authenticate General Manager",
                method="POST",
                path="{{api_prefix}}/auth/login",
                body_json={
                    "username": "{{gm_username}}",
                    "password": "{{gm_password}}"
                },
                tests=[
                    "pm.test('GM login successful', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var token = pm.response.json().data.accessToken;",
                    "    pm.environment.set('gm_token', token);",
                    "});"
                ],
                description="Step 2: Obtain fresh General Manager JWT token."
            ),
            make_request(
                name="L-03: Create Lifecycle Non-IATA Account",
                method="POST",
                path="{{api_prefix}}/non-iatas",
                auth_var="finance_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('lifecycle_code', 'LC_' + suffix);",
                    "pm.environment.set('lifecycle_name', 'Lifecycle Agency ' + suffix);",
                    "pm.environment.set('lifecycle_email', 'lifecycle_' + suffix + '@amoudi.com');"
                ],
                body_json={
                    "code": "{{lifecycle_code}}",
                    "name": "{{lifecycle_name}}",
                    "contactName": "Lifecycle Manager",
                    "phone": "+251911777000",
                    "email": "{{lifecycle_email}}"
                },
                tests=[
                    "pm.test('Lifecycle account created', function () {",
                    "    pm.expect(pm.response.code).to.be.oneOf([200, 201]);",
                    "    var id = pm.response.json().data.id;",
                    "    pm.environment.set('lifecycle_non_iata_id', id);",
                    "    console.log('Created lifecycle Non-IATA with ID: ' + id);",
                    "});"
                ],
                description="Step 3: Registers a dedicated, isolated Non-IATA for the lifecycle test."
            ),
            make_request(
                name="L-04: Verify Initial Financial Summary (Zero Balance)",
                method="GET",
                path="{{api_prefix}}/financial-summaries/{{lifecycle_non_iata_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Initial state: zero sales, zero payments, zero balance', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalSales)).to.eql(0);",
                    "    pm.expect(Number(d.totalPayments)).to.eql(0);",
                    "    pm.expect(Number(d.outstandingBalance)).to.eql(0);",
                    "});"
                ],
                description="Step 4: Confirms zero balance on brand new Non-IATA account."
            ),
            make_request(
                name="L-05: GM Records Payment #1 (12,000 ETB)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="gm_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('rec_lc1', 'REC-LC1-' + suffix);",
                    "pm.environment.set('ref_lc1', 'REF-LC1-' + suffix);"
                ],
                body_json={
                    "nonIataId": "{{lifecycle_non_iata_id}}",
                    "amount": 12000.00,
                    "currency": "ETB",
                    "paymentDate": "{{current_date}}",
                    "receiptNumber": "{{rec_lc1}}",
                    "paymentReference": "{{ref_lc1}}"
                },
                tests=[
                    "pm.test('Payment #1 recorded with POSTED status', function () {",
                    "    pm.response.to.have.status(201);",
                    "    var payId = pm.response.json().data.id;",
                    "    pm.environment.set('lifecycle_payment_1_id', payId);",
                    "    console.log('Created payment #1 with ID: ' + payId);",
                    "});"
                ],
                description="Step 5: General Manager posts payment of 12,000 ETB."
            ),
            make_request(
                name="L-06: Verify Financial Summary Reflects Credit (-12,000 ETB)",
                method="GET",
                path="{{api_prefix}}/financial-summaries/{{lifecycle_non_iata_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Summary reflects -12,000.00 ETB outstanding balance', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalPayments)).to.eql(12000.00);",
                    "    pm.expect(Number(d.outstandingBalance)).to.eql(-12000.00);",
                    "});"
                ],
                description="Step 6: Verifies overpayment derivation (Sales 0 - Payments 12,000 = -12,000 ETB)."
            ),
            make_request(
                name="L-07: GM Records Payment #2 (8,000 ETB)",
                method="POST",
                path="{{api_prefix}}/payments",
                auth_var="gm_token",
                prerequest=[
                    "var suffix = Date.now().toString().slice(-6);",
                    "pm.environment.set('rec_lc2', 'REC-LC2-' + suffix);",
                    "pm.environment.set('ref_lc2', 'REF-LC2-' + suffix);"
                ],
                body_json={
                    "nonIataId": "{{lifecycle_non_iata_id}}",
                    "amount": 8000.00,
                    "currency": "ETB",
                    "paymentDate": "{{current_date}}",
                    "receiptNumber": "{{rec_lc2}}",
                    "paymentReference": "{{ref_lc2}}"
                },
                tests=[
                    "pm.test('Payment #2 recorded successfully', function () {",
                    "    pm.response.to.have.status(201);",
                    "    var payId = pm.response.json().data.id;",
                    "    pm.environment.set('lifecycle_payment_2_id', payId);",
                    "    console.log('Created payment #2 with ID: ' + payId);",
                    "});"
                ],
                description="Step 7: General Manager posts additional payment of 8,000 ETB."
            ),
            make_request(
                name="L-08: Verify Financial Summary Reflects Cumulative Payments (-20,000 ETB)",
                method="GET",
                path="{{api_prefix}}/financial-summaries/{{lifecycle_non_iata_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Summary reflects total payments = 20,000.00, balance = -20,000.00', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalPayments)).to.eql(20000.00);",
                    "    pm.expect(Number(d.outstandingBalance)).to.eql(-20000.00);",
                    "});"
                ],
                description="Step 8: Verifies accumulation of multiple POSTED payments."
            ),
            make_request(
                name="L-09: Verify Account Statement Contains Both Payments",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{lifecycle_non_iata_id}}/statement",
                auth_var="finance_token",
                tests=[
                    "pm.test('Statement closing balance is -20,000.00 ETB', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalPayments)).to.eql(20000.00);",
                    "    pm.expect(Number(d.closingBalance)).to.eql(-20000.00);",
                    "    pm.expect(d.transactionCount).to.eql(2);",
                    "});"
                ],
                description="Step 9: Confirms statement reflects credit transactions and closing balance."
            ),
            make_request(
                name="L-10: Verify Aging Report Reflects Non-IATA Account",
                method="GET",
                path="{{api_prefix}}/aging?nonIataId={{lifecycle_non_iata_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Aging report reflects matching account balance', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    if (d.rows && d.rows.length > 0) {",
                    "        pm.expect(Number(d.rows[0].totalOutstanding)).to.eql(-20000.00);",
                    "    }",
                    "});"
                ],
                description="Step 10: Confirms aging report row reflects accurate balance."
            ),
            make_request(
                name="L-11: Finance Cancels Payment #1 (12,000 ETB)",
                method="POST",
                path="{{api_prefix}}/payments/{{lifecycle_payment_1_id}}/cancel",
                auth_var="finance_token",
                tests=[
                    "pm.test('Payment #1 cancelled by Finance', function () {",
                    "    pm.response.to.have.status(200);",
                    "    pm.expect(pm.response.json().data.status).to.eql('CANCELLED');",
                    "});"
                ],
                description="Step 11: Finance Officer initiates payment cancellation."
            ),
            make_request(
                name="L-12: Verify Payment #1 Status is CANCELLED",
                method="GET",
                path="{{api_prefix}}/payments/{{lifecycle_payment_1_id}}",
                auth_var="gm_token",
                tests=[
                    "pm.test('Payment #1 status is confirmed CANCELLED', function () {",
                    "    pm.response.to.have.status(200);",
                    "    pm.expect(pm.response.json().data.status).to.eql('CANCELLED');",
                    "});"
                ],
                description="Step 12: Independent verification of payment cancellation status."
            ),
            make_request(
                name="L-13: Verify Financial Summary Reconciles (Payments = 8,000 ETB)",
                method="GET",
                path="{{api_prefix}}/financial-summaries/{{lifecycle_non_iata_id}}",
                auth_var="finance_token",
                tests=[
                    "pm.test('Summary reconciles immediately: total payments = 8,000, balance = -8,000', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalPayments)).to.eql(8000.00);",
                    "    pm.expect(Number(d.outstandingBalance)).to.eql(-8000.00);",
                    "});"
                ],
                description="Step 13: Proves cancelled payments are strictly excluded from payment summation."
            ),
            make_request(
                name="L-14: Verify Statement Closing Balance Reconciles (-8,000 ETB)",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{lifecycle_non_iata_id}}/statement",
                auth_var="finance_token",
                tests=[
                    "pm.test('Statement reflects closing balance = -8,000.00 ETB', function () {",
                    "    pm.response.to.have.status(200);",
                    "    var d = pm.response.json().data;",
                    "    pm.expect(Number(d.totalPayments)).to.eql(8000.00);",
                    "    pm.expect(Number(d.closingBalance)).to.eql(-8000.00);",
                    "});"
                ],
                description="Step 14: Proves statement ledger reflects payment reversal."
            ),
            make_request(
                name="L-15: Export Final Reconciled Statement to Excel",
                method="GET",
                path="{{api_prefix}}/non-iatas/{{lifecycle_non_iata_id}}/statement/export/excel",
                auth_var="finance_token",
                tests=[
                    "pm.test('Status code is 200 OK', function () {",
                    "    pm.response.to.have.status(200);",
                    "});",
                    "if (pm.response.code === 200) {",
                    "    pm.test('Excel file generated successfully', function () {",
                    "        pm.expect(pm.response.headers.get('Content-Type')).to.include('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');",
                    "        pm.expect(pm.response.responseSize).to.be.above(0);",
                    "    });",
                    "}"
                ],
                description="Step 15: Concludes lifecycle test with successful binary report generation."
            )
        ]
    }

    collection["item"] = [
        folder_health,
        folder_auth,
        folder_non_iatas,
        folder_sales,
        folder_imports,
        folder_summaries,
        folder_payments,
        folder_statements,
        folder_aging,
        folder_audit,
        folder_security,
        folder_lifecycle
    ]

    return collection

if __name__ == "__main__":
    col = build_collection()
    out_path = os.path.join(os.path.dirname(__file__), "Amoudi-Financial-Management.postman_collection.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(col, f, indent=2)
    print(f"Collection successfully generated at: {out_path}")
    print(f"Total Folders: {len(col['item'])}")
    total_reqs = sum(len(f['item']) for f in col['item'])
    print(f"Total Requests: {total_reqs}")
