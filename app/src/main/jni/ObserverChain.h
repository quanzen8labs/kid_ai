//
// Created by macbook on 04/10/2022.
//

#ifndef OBSERVERCHAIN_H
#define OBSERVERCHAIN_H

#include <jni.h>

class ObserverChain {
public:
    ObserverChain(jweak pJobject, jmethodID pID);

    jweak store_Wlistener=NULL;
    jmethodID store_method = NULL;
};
#endif //OBSERVERCHAIN_H
