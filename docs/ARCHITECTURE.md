# Architettura Sistema RFID

## Panoramica

```
┌─────────────────────────────────────────────────────────────┐
│                     ANDROID APPLICATION                      │
│  ┌────────────┐    ┌──────────────┐    ┌─────────────┐     │
│  │ MainActivity│───▶│ RFIDViewModel│───▶│RetrofitClient│    │
│  └────────────┘    └──────────────┘    └─────────────┘     │
│         │                  │                    │            │
│         │                  ▼                    │            │
│         │          ┌──────────────┐             │            │
│         └─────────▶│ RFIDManager  │             │            │
│                    └──────────────┘             │            │
│                           │                     │            │
└───────────────────────────┼─────────────────────┼────────────┘
                            │                     │
                    ┌───────▼───────┐             │
                    │  Zebra RFD8500│             │
                    │   (Bluetooth)  │             │
                    └───────────────┘             │
                            │                     │
                    ┌───────▼───────┐             │
                    │  RFID Tags    │             │
                    │   (UHF Gen2)  │             │
                    └───────────────┘             │
                                                  │
                                        ┌─────────▼─────────┐
                                        │   REST API        │
                                        │  (HTTP/JSON)      │
                                        └─────────┬─────────┘
                                                  │
┌─────────────────────────────────────────────────┼────────────┐
│                    BACKEND SERVER                │            │
│                                        ┌─────────▼─────────┐ │
│                                        │   Express.js      │ │
│                                        │   (server.js)     │ │
│                                        └─────────┬─────────┘ │
│                                                  │            │
│                    ┌─────────────────────────────┼──────────┐│
│                    │         Middleware          │          ││
│                    │  CORS, Helmet, Morgan, Body Parser    ││
│                    └─────────────────────────────┼──────────┘│
│                                                  │            │
│              ┌──────────────┬──────────────────┐│            │
│              │              │                  ││            │
│      ┌───────▼─────┐ ┌─────▼──────┐           ││            │
│      │ rfid.routes │ │ tags.routes│           ││            │
│      └───────┬─────┘ └─────┬──────┘           ││            │
│              │              │                  ││            │
│      ┌───────▼──────┐ ┌────▼──────────┐       ││            │
│      │rfidController│ │tagsController │       ││            │
│      └───────┬──────┘ └────┬──────────┘       ││            │
│              │              │                  ││            │
│              └──────────────┴──────────────────┘│            │
│                             │                   │            │
│                    ┌────────▼─────────┐         │            │
│                    │  Database Pool   │         │            │
│                    │   (pg module)    │         │            │
│                    └────────┬─────────┘         │            │
└─────────────────────────────┼───────────────────┼────────────┘
                              │                   │
                     ┌────────▼────────┐          │
                     │   PostgreSQL    │          │
                     │   (rfid_db)     │          │
                     └─────────────────┘          │
                              │                   │
              ┌───────────────┼───────────────┐   │
              │               │               │   │
       ┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼───────┐
       │ rfid_tags   │ │read_sessions│ │session_tags │
       │ (EPC,RSSI,  │ │(session_id, │ │(join table) │
       │  metadata)  │ │ timestamps) │ │             │
       └─────────────┘ └─────────────┘ └─────────────┘
```

## Data Flow - Scan Operation

```
1. User taps "Avvia Scansione"
   │
   ▼
2. RFIDViewModel.startScan()
   │
   ├─▶ Generate sessionId (UUID)
   │
   ├─▶ POST /api/rfid/sessions/start
   │   └─▶ Backend creates session in DB
   │
   └─▶ RFIDManager.startInventory()
       │
       ▼
3. RFD8500 scans RFID tags
   │
   ▼
4. Zebra SDK fires TagReadEventListener
   │
   ▼
5. RFIDManager.handleTagRead()
   │
   └─▶ Updates StateFlow<List<TagData>>
       │
       ▼
6. RFIDViewModel observes tags flow
   │
   └─▶ For each new tag:
       │
       └─▶ POST /api/rfid/scan
           └─▶ Backend:
               ├─▶ INSERT/UPDATE rfid_tags
               └─▶ INSERT session_tags (if sessionId)

7. User taps "Ferma Scansione"
   │
   ▼
8. RFIDViewModel.stopScan()
   │
   ├─▶ RFIDManager.stopInventory()
   │
   └─▶ POST /api/rfid/sessions/{id}/end
       └─▶ Backend updates session end_time
```

## Component Responsibilities

### Android App

**MainActivity**
- UI management e user interactions
- Permission handling (Bluetooth, Location)
- Observe ViewModel LiveData/StateFlow
- Lifecycle management

**RFIDViewModel**
- Business logic coordination
- State management (connection, scanning, tag count)
- API calls orchestration
- Session lifecycle management

**RFIDManager**
- Zebra SDK wrapper
- Bluetooth connection to RFD8500
- Reader configuration (power, session, target)
- Tag event handling
- StateFlow emissions

**RetrofitClient**
- HTTP client singleton
- Base URL configuration
- Logging interceptor
- GSON converter

### Backend

**server.js**
- Express app initialization
- Middleware setup
- Route registration
- Error handling
- Server startup

**Routes**
- Endpoint definitions
- Request routing to controllers
- RESTful URL structure

**Controllers**
- Business logic implementation
- Database queries
- Transaction management
- Error handling
- Response formatting

**Database Pool**
- Connection pooling
- Query execution
- Connection lifecycle

### Database

**rfid_tags**
- Persistent tag storage
- Read count tracking
- RSSI history
- Metadata (JSONB for flexibility)

**read_sessions**
- Session tracking
- Device identification
- Timestamp tracking
- Status management

**session_tags**
- Many-to-many relationship
- Session-specific tag data
- Read timestamps per session

## Technology Stack

### Android
- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM
- **Async**: Coroutines + Flow
- **DI**: Manual (può essere esteso con Hilt/Koin)
- **Network**: Retrofit 2 + OkHttp
- **Bluetooth RFID**: Zebra RFID API 3

### Backend
- **Runtime**: Node.js 16+
- **Framework**: Express 4
- **Database Driver**: node-postgres (pg)
- **Middleware**: CORS, Helmet, Morgan, Body-Parser
- **Environment**: dotenv

### Database
- **DBMS**: PostgreSQL 13+
- **Connection**: Connection Pool
- **Schema**: Relational con JSONB per metadata

## Security Considerations

### Android
- Runtime permissions (Bluetooth, Location)
- HTTPS in production (cleartext traffic disabled)
- ProGuard rules per obfuscation
- Secure storage per credentials (se implementato)

### Backend
- Helmet per security headers
- CORS configuration
- Input validation (da implementare)
- SQL injection prevention (parametrized queries)
- Environment variables per secrets
- Rate limiting (da implementare)

### Database
- Credenziali in environment variables
- Connection pooling per performance
- Foreign key constraints per integrità
- Indici per query optimization

## Scalability Notes

**Current Design**: Single server, direct device-to-server communication

**Potential Improvements**:
- WebSocket per real-time updates
- Redis cache per sessioni attive
- Message queue (RabbitMQ/Redis) per tag processing
- Multiple backend instances con load balancer
- Database replication per read scaling
- Batch insert per tag (invece di single inserts)
