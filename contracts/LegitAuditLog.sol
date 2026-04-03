// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * LegitAuditLog - Blockchain Audit Trail Contract
 * 
 * This contract records immutable audit logs for the Legit platform on Polygon Amoy testnet.
 * All verification events, document anchors, and key burns are permanently recorded on-chain.
 */
contract LegitAuditLog {
    
    // Events
    event VerificationLogged(
        bytes32 indexed contractHash,
        address indexed requester,
        bool passed,
        string overallStatus,
        uint256 timestamp
    );
    
    event DocumentAnchored(
        bytes32 indexed documentHash,
        address indexed uploader,
        uint256 timestamp
    );
    
    event KeyBurned(
        bytes32 indexed contractHash,
        address indexed requester,
        uint256 timestamp
    );
    
    // Structs
    struct VerificationRecord {
        bytes32 contractHash;
        address requester;
        bool passed;
        string overallStatus;
        uint256 timestamp;
    }
    
    struct DocumentAnchor {
        bytes32 documentHash;
        address uploader;
        uint256 timestamp;
    }
    
    // Storage
    mapping(bytes32 => VerificationRecord) public verifications;
    mapping(bytes32 => DocumentAnchor) public documents;
    mapping(bytes32 => uint256) public keyBurns;
    
    // Log a verification result
    function logVerification(
        bytes32 contractHash,
        bool passed,
        string memory overallStatus
    ) external {
        require(verifications[contractHash].timestamp == 0, "Verification already logged");
        
        verifications[contractHash] = VerificationRecord({
            contractHash: contractHash,
            requester: msg.sender,
            passed: passed,
            overallStatus: overallStatus,
            timestamp: block.timestamp
        });
        
        emit VerificationLogged(contractHash, msg.sender, passed, overallStatus, block.timestamp);
    }
    
    // Anchor a document hash
    function anchorDocument(bytes32 documentHash) external {
        documents[documentHash] = DocumentAnchor({
            documentHash: documentHash,
            uploader: msg.sender,
            timestamp: block.timestamp
        });
        
        emit DocumentAnchored(documentHash, msg.sender, block.timestamp);
    }
    
    // Log a key burn event
    function logKeyBurn(bytes32 contractHash) external {
        keyBurns[contractHash] = block.timestamp;
        emit KeyBurned(contractHash, msg.sender, block.timestamp);
    }
    
    // Query functions
    function getVerification(bytes32 contractHash) external view returns (
        address requester,
        bool passed,
        string memory overallStatus,
        uint256 timestamp
    ) {
        VerificationRecord memory record = verifications[contractHash];
        return (record.requester, record.passed, record.overallStatus, record.timestamp);
    }
    
    function getDocumentAnchor(bytes32 documentHash) external view returns (
        address uploader,
        uint256 timestamp
    ) {
        DocumentAnchor memory anchor = documents[documentHash];
        return (anchor.uploader, anchor.timestamp);
    }
    
    function isDocumentAnchored(bytes32 documentHash) external view returns (bool) {
        return documents[documentHash].timestamp > 0;
    }
    
    function isVerificationLogged(bytes32 contractHash) external view returns (bool) {
        return verifications[contractHash].timestamp > 0;
    }
}
