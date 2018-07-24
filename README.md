# collabypto
Collabypto is a real-time collaborative text editor with end-to-end encryption using Operational Transformation and the Signal Protocol.


# Installation instructions:

1. Download the "jars" folder.
2. Run server and/or client jars. Make sure the "raw" folder is in the same folder as the client jar. 
    1. If on Linux, "raw" folder must be placed in "home"
3. Enjoy!

This project is based on the following open source collaborative editing program and Signal Protocol Library for Java:  
* https://github.com/nhvtgd/Real-Time-Collaborative-Editing/tree/master/hanwenxu-viettran-yygu
* https://github.com/signalapp/libsignal-protocol-java

The following classes from the source project were modified: CollabClient, CollabServer, CollabModel, LoginPage, DocumentSelectionPage, ClientGui, ServerGui   
The following classes were added: The 'signal' folder, DocumentInstance, DocumentState, UserInfo
