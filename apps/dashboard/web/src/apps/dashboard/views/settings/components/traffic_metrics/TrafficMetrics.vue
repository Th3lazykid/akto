<template>
    <div class="pa-4">
        <div class="filter-div">
            <v-menu offset-y v-model="menu" :close-on-content-click="false"> 
                <template v-slot:activator="{ on, attrs }">
                    <secondary-button 
                        :text= groupByBtnText
                        v-bind="attrs"
                        v-on="on"
                        color="#6200EA"
                    />
                </template>
                <nested-filter-list :items="groupByOptions" v-if="menu" width="200px" @clicked="clickedGroupByFilter"/>
            </v-menu>
        </div>
        <div v-if="!loading">
            <div v-for="(trafficTrend, name) in trafficTrendArr">
                <div>{{ name }}</div>
                <line-chart
                    type='spline'
                    color='var(--themeColor)'
                    :areaFillHex="true"
                    :height="230"
                    title="Traffic"
                    :data="trafficTrend"
                    :defaultChartOptions="{legend:{enabled: false}}"
                    background-color="var(--transparent)"
                    :text="true"
                    :input-metrics="[]"
                    class="pa-5"
                />
            </div>
        </div>
        <div v-else class="spinner-div">
            <spinner :size="50" color="var(--themeColor)"/>
        </div>
    </div>
</template>

<script>
import LineChart from '@/apps/dashboard/shared/components/LineChart'
import func from "@/util/func";
import SecondaryButton from "@/apps/dashboard/shared/components/buttons/SecondaryButton.vue";
import FilterList from '@/apps/dashboard/shared/components/FilterList'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import api from '@/apps/dashboard/views/settings/components/traffic_metrics/api.js';
import NestedFilterList from '@/apps/dashboard/shared/components/NestedFilterList'

export default {
    name: "TrafficMetrics",
    components: {
        LineChart,
        SecondaryButton,
        FilterList,
        Spinner,
        NestedFilterList
    },
    data() {
        return {
            loading: false,
            hosts: [],
            names: [
              'OUTGOING_PACKETS_MIRRORING', 'OUTGOING_REQUESTS_MIRRORING', 'INCOMING_PACKETS_MIRRORING', 'TOTAL_REQUESTS_RUNTIME',
              'FILTERED_REQUESTS_RUNTIME'
            ],
            groupBy: { title: "All", value: "ALL" },
            startTimestamp: Math.floor(Date.now() / 1000) - (7 * 24 * 60 * 60),
            endTimestamp: Math.floor(Date.now() / 1000) ,
            trafficMetricsMapString: '',
            trafficMetricsMap: {},
            menu: false

        }
    },
    methods: {
        clickedGroupByFilter(item) {
            this.menu = false
            this.groupBy = item['parent']
            let filter = {}
            if (item['nestedValue']) {
                filter = {"host": item['nestedValue']['value']}
            }
            this.fetchTrafficMetrics(this.startTimestamp, this.endTimestamp, filter)
        },
        async fetchTrafficMetrics(startTimestamp, endTimestamp, filter) {
            this.loading = true
            this.trafficMetricsMap = {}
            let host = filter['host']
            let resp = await api.fetchTrafficMetrics(this.groupBy.value, startTimestamp, endTimestamp, this.names, host)
            this.trafficMetricsMap = resp['trafficMetricsMap']
            this.loading = false
        }

    },
    async mounted() {
        this.fetchTrafficMetrics(this.startTimestamp, this.endTimestamp, {})
        this.hosts = func.getListOfHosts(this.$store.state.collections.apiCollections)
    },
    computed: {
        trafficTrendArr() {
            let result = {}
            for (const [key, countMap] of Object.entries(this.trafficMetricsMap)) {
                let val = func.convertTrafficMetricsToTrend(countMap)
                result[key] =val
            }
            return result
        },
        groupByBtnText() {
            let title = this.groupBy.title
            console.log(title);
            return title === "All" ? title : "Group by " + title 
        },
        ipSelected() {
            return this.groupBy.value === 'IP'
        },

        groupByOptions() {
            return [           
                { title: "All", value: "ALL" },
                { title: "Host", value: "HOST" },
                { title: "Target group", value: "VXLANID" },
                { title: "IP", value: "IP", nested: {
                    "title": "Select host",
                    "values": this.hosts
                } }
            ]
        }
    }
}
</script>

<style lang="sass" scoped>
.traffic-main
    padding: 24px

.filter-div
    display: flex
    justify-content: flex-end

.spinner-div
    display: flex
    justify-content: center
    height: calc(100vh - 220px)
    align-items: center

</style>
