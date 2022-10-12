//
// Created by macbook on 04/10/2022.
//

//
// Created by n.maletskiy on 23.07.2018.
//

#include "ObserverChain.h"

ObserverChain::ObserverChain(jweak pJobject, jmethodID pID) {
    store_Wlistener=pJobject;
    store_method = pID;
}