import { TreemapChart } from 'echarts/charts';
import { MarkPointComponent } from 'echarts/components';

import { echarts } from './echarts-setup';

echarts.use([TreemapChart, MarkPointComponent]);

export { echarts };
