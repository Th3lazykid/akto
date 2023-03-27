import request from '@/util/request'

export default {
    getAllCollections () {
        return request({
            url: '/api/getAllCollections',
            method: 'post',
            data: {}
        }).then((resp) => {
            return resp
        })
    },

    createCollection(name) {
        return request({
            url: '/api/createCollection',
            method: 'post',
            data: {collectionName:name}
        }).then((resp) => {
            return resp
        })
    },

    deleteCollection(apiCollectionId, isLogicalGroup) {
        return request({
            url: '/api/deleteCollection',
            method: 'post',
            data: {apiCollectionId, isLogicalGroup}
        }).then((resp) => {
            return resp
        })
    },

    deleteMultipleCollections(items) {
        return request({
            url: '/api/deleteMultipleCollections',
            method: 'post',
            data: {apiCollectionResponse: items}
        })        
    }
}