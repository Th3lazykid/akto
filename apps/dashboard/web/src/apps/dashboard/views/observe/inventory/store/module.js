import Vue from 'vue'
import Vuex from 'vuex'
import api from '../api'
import func from "@/util/func"


Vue.use(Vuex)

var state = {
    loading: false,
    fetchTs: 0,
    apiCollectionId: 0,
    apiCollectionName: '',
    apiCollection: [],
    sensitiveParams: [],
    swaggerContent : null,
    apiInfoList: [],
    filters: [],
    lastFetched: 0,
    parameters: [],
    url: '',
    method: ''
}

let functionCompareParamObj = (x, p) => {
    return x.url === p.url && x.method === p.method && x.apiCollectionId === p.apiCollectionId
}

const inventory = {
    namespaced: true,
    state: state,
    mutations: {
        EMPTY_STATE (state) {
            state.loading = false
            state.fetchTs = 0
            state.apiCollectionId = 0
            state.apiCollection = []
            state.apiCollectionName = ''
            state.swaggerContent = null
            state.parameters = []
            state.url = ''
            state.method = ''
        },
        EMPTY_PARAMS (state) {
            state.loading = false
            state.parameters = []
            state.url = ''
            state.method = ''
        },
        SAVE_API_COLLECTION (state, info) {
            state.apiCollectionId = info.apiCollectionId
            state.apiCollectionName = info.data.name
            state.apiCollection = info.data.endpoints.map(x => {return {...x._id, startTs: x.startTs}})
            state.apiInfoList = info.data.apiInfoList

        },
        TOGGLE_SENSITIVE (state, p) {
            let sensitiveParamIndex = state.sensitiveParams.findIndex(x => {
                return functionCompareParamObj(x, p)
            })

            let apiCollectionIndex = state.apiCollection.findIndex(x => {
                return functionCompareParamObj(x, p)
            })

            let savedAsSensitive = sensitiveParamIndex < 0
            if (savedAsSensitive) {
                state.sensitiveParams.push(p)
            } else {
                state.sensitiveParams.splice(sensitiveParamIndex, 1)
            }

            if (apiCollectionIndex > -1) {
                state.apiCollection[apiCollectionIndex].savedAsSensitive = savedAsSensitive
            }
            state.apiCollection = [...state.apiCollection]
        },
        SAVE_SENSITIVE (state, fields) {
            state.sensitiveParams = fields
            
            fields.forEach(p => {
                let apiCollectionIndex = state.apiCollection.findIndex(x => {
                    return functionCompareParamObj(x, p)
                })
                
                if (apiCollectionIndex > -1) {
                    if (!state.apiCollection[apiCollectionIndex].sensitive) {
                        state.apiCollection[apiCollectionIndex].sensitive = new Set()
                    }
                    if (!p.subType) {
                        p.subType = {"name": "CUSTOM"}
                    }
                    state.apiCollection[apiCollectionIndex].sensitive.add(p.subType)
                }
            })
            state.apiCollection = [...state.apiCollection]
        },
        SAVE_PARAMS(state, {method, url, parameters}) {
            state.method = method
            state.url = url
            state.parameters = parameters
        }
    },
    actions: {
        emptyState({commit}, payload, options) {
            commit('EMPTY_STATE', payload, options)
        },
        loadAPICollection({commit}, {apiCollectionId, shouldLoad}, options) {
            state.lastFetched = new Date() / 1000
            commit('EMPTY_STATE')
            if (shouldLoad) {
                state.loading = true
            }
            return api.fetchAPICollection(apiCollectionId).then((resp) => {
                commit('SAVE_API_COLLECTION', {data: resp.data, apiCollectionId: apiCollectionId}, options)
                api.loadSensitiveParameters(apiCollectionId).then(allSensitiveFields => {
                    commit('SAVE_SENSITIVE', allSensitiveFields.data.endpoints)
                })
                api.loadContent(apiCollectionId).then(resp => {
                    if(resp && resp.data && resp.data.content)
                        state.swaggerContent = JSON.parse(resp.data.content)
                })
                api.fetchFilters().then(resp => {
                    let a = resp.runtimeFilters
                    a.forEach(x => {
                        state.filters[x.customFieldName] = x
                    })
                })
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        loadParamsOfEndpoint({commit}, {apiCollectionId, url, method}) {
            commit('EMPTY_PARAMS')
            state.loading = true    
            return api.loadParamsOfEndpoint(apiCollectionId, url, method).then(resp => {
                api.loadSensitiveParameters(apiCollectionId, url, method).then(allSensitiveFields => {
                    allSensitiveFields.data.endpoints.filter(x => x.sensitive).forEach(sensitive => {
                        let index = resp.data.params.findIndex(x => 
                            x.param === sensitive.param && 
                            x.isHeader === sensitive.isHeader && 
                            x.responseCode === sensitive.responseCode    
                        )

                        if (index > -1 && !sensitive.subType) {
                            resp.data.params[index].savedAsSensitive = true
                            if (!resp.data.params[index].subType) {
                                resp.data.params[index].subType = {"name": "CUSTOM"}
                            } else {
                                resp.data.params[index].subType = JSON.parse(JSON.stringify(resp.data.params[index].subType))
                            }
                        }

                    })
                })
                commit('SAVE_PARAMS', {parameters: resp.data.params, apiCollectionId, url, method})
            })

        },
        toggleSensitiveParam({commit}, paramInfo) {
            return api.addSensitiveField(paramInfo).then(resp => {
                commit('TOGGLE_SENSITIVE', paramInfo)
                return resp
            })
        },
        uploadHarFile({commit,state},{content,filename, skipKafka}) {
            return api.uploadHarFile(content,state.apiCollectionId,skipKafka).then(resp => {
                return resp
            })
        },
        downloadOpenApiFile({commit,state}) {
            return api.downloadOpenApiFile(state.apiCollectionId).then(resp => {
                return resp
            })
        },
        exportToPostman({commit,state}) {
            return api.exportToPostman(state.apiCollectionId).then(resp => {
                return resp
            })
        },
        saveContent({ commit, dispatch, state }, {swaggerContent, filename, apiCollectionId}) {
            state.loading = true
            api.saveContent({swaggerContent, filename, apiCollectionId}).then(resp => {
                state.filename = filename
                state.swaggerContent = swaggerContent
                state.apiCollectionId = apiCollectionId
                state.loading = false
            }).catch(() => {
                state.loading = false
            })
        },
        fetchApiInfoList({commit,dispatch, state}, {apiCollectionId}) {
            api.fetchApiInfoList(apiCollectionId).then(resp => {
              state.apiInfoList = resp.apiInfoList
            })
        },
        fetchFilters({commit, dispatch, state}) {
            api.fetchFilters().then(resp => {
              let a = resp.runtimeFilters
              a.forEach(x => {
                state.filters[x.customFieldName] = x
              })
            })
        }
    },
    getters: {
        getFetchTs: (state) => state.fetchTs,
        getLoading: (state) => state.loading,
        getAPICollection: (state) => state.apiCollection,
        getAPICollectionId: (state) => state.apiCollectionId,
        getAPICollectionName: (state) => state.apiCollectionName,
        isSensitive: (state) => p => state.sensitiveParams && state.sensitiveParams.findIndex(x => {
            return functionCompareParamObj(x, p)
        }) > 0,
        getApiInfoList: (state) => state.apiInfoList,
        getFilters: (state) => state.filters,
    }
}

export default inventory