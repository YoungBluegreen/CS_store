import argparse
import logging
import os
import random
import sys
import numpy as np
import torch
import torch.backends.cudnn as cudnn
import time
import torch.nn as nn
from torch.utils.data import DataLoader
from tqdm import tqdm
from datasets.dataset_synapse import Synapse_dataset
from utilsOUR import test_single_volume
from torchinfo import summary
from networks.vit_seg_modeling_OUR import VisionTransformer as ViT_seg
from networks.vit_seg_modeling_OUR import CONFIGS as CONFIGS_ViT_seg


parser = argparse.ArgumentParser()
parser.add_argument('--volume_path', type=str,
                    default='/home/ahu/桌面/file/TransFuseNet/TransFuseNet/data/Synapse/test_vol_h5', help='root dir for validation volume data')  # for acdc volume_path=root_dir
parser.add_argument('--dataset', type=str,
                    default='Synapse', help='experiment_name')
parser.add_argument('--num_classes', type=int,
                    default=6, help='output channel of network')
parser.add_argument('--list_dir', type=str,
                    default='/home/ahu/桌面/file/TransFuseNet/TransFuseNet/lists/lists_Synapse', help='list dir')

parser.add_argument('--max_iterations', type=int,default=20000, help='maximum epoch number to train')
parser.add_argument('--max_epochs', type=int, default=100, help='maximum epoch number to train')
parser.add_argument('--batch_size', type=int, default=24,
                    help='batch_size per gpu')
parser.add_argument('--img_size', type=int, default=224, help='input patch size of network input')
parser.add_argument('--is_savenii', action='store_true', default=True, help='Save nii files')

parser.add_argument('--n_skip', type=int, default=3, help='using number of skip-connect, default is num')
parser.add_argument('--vit_name', type=str, default='R50-ViT-B_16', help='select one vit model')

parser.add_argument('--test_save_dir', type=str, default='/home/ahu/桌面/file/TransUNet(1)/TransUNet/predictions', help='saving prediction as nii!')
parser.add_argument('--deterministic', type=int,  default=1, help='whether use deterministic training')
parser.add_argument('--base_lr', type=float,  default=0.0133, help='segmentation network learning rate')
parser.add_argument('--seed', type=int, default=1234, help='random seed')
parser.add_argument('--vit_patches_size', type=int, default=16, help='vit_patches_size, default is 16')
args = parser.parse_args()


def inference(args, model, test_save_path=None):
    model.eval()
    model_info = summary(model, input_size=(1, 3, 224, 224), verbose=0)
    flops = model_info.total_mult_adds
    params = model_info.total_params
    logging.info('Model FLOPs: %d' % flops)
    logging.info('Model Parameters: %d' % params)
    logging.info('Model Parameters (Total): {:.2f} M'.format(params / 1e6))

    db_test = args.Dataset(base_dir=args.volume_path, split="test_vol", list_dir=args.list_dir)
    testloader = DataLoader(db_test, batch_size=1, shuffle=False, num_workers=1)
    logging.info("{} test iterations per epoch".format(len(testloader)))

    metric_list = np.zeros((args.num_classes, 5))
    valid_sample_count = np.zeros((args.num_classes, 5))
    total_infer_time = 0
    pa_list = []
    recall_list = []
    f1_list = []

    for i_batch, sampled_batch in tqdm(enumerate(testloader)):
        image, label, case_name = sampled_batch["image"], sampled_batch["label"], sampled_batch['case_name'][0]

        metric_i, infer_time, pa, recall, f1 = test_single_volume(image, label, model, classes=args.num_classes,
                                                  patch_size=[args.img_size, args.img_size],
                                                  test_save_path=test_save_path, case=case_name,
                                                  z_spacing=args.z_spacing)
        total_infer_time += infer_time
        pa_list.append(pa)
        recall_list.append(recall)
        f1_list.append(f1)

        for i in range(args.num_classes):
            for j in range(5):
                if metric_i[i][j] > 0:
                    metric_list[i][j] += metric_i[i][j]
                    valid_sample_count[i][j] += 1

        logging.info('idx %d case %s', i_batch, case_name)
        for i in range(args.num_classes):
            if isinstance(metric_i[i], tuple):
                logging.info('Mean class %d mean_dice %f mean_hd95 %f acc %f miou %f oa %f' %
                             (i + 1, *metric_i[i]))

    for i in range(args.num_classes):
        for j in range(5):
            if valid_sample_count[i][j] > 0:
                metric_list[i][j] /= valid_sample_count[i][j]

    for i in range(args.num_classes):
        logging.info('Mean class %d mean_dice %f mean_hd95 %f acc %f miou %f oa %f' %
                     (i + 1, metric_list[i][0], metric_list[i][1], metric_list[i][2],
                      metric_list[i][3], metric_list[i][4]))

    performance = np.mean(metric_list[:, 0])
    mean_hd95 = np.mean(metric_list[:, 1])
    acc = np.mean(metric_list[:, 2])
    iou = np.mean(metric_list[:, 3])
    oa = np.mean(metric_list[:, 4])

    avg_pa = np.mean(pa_list)
    avg_recall = np.mean(recall_list)
    avg_f1 = np.mean(f1_list)

    logging.info('Testing performance: mean_dice : %f mean_hd95 : %f acc : %f miou : %f oa : %f' %
                 (performance, mean_hd95, acc, iou, oa))
    logging.info('Additional Metrics: Pixel Accuracy (PA): %.4f, Recall: %.4f, F1-score: %.4f' % (avg_pa, avg_recall, avg_f1))

    avg_infer_time = total_infer_time / len(testloader)
    logging.info('Average inference time: %f seconds' % avg_infer_time)

    return "Testing Finished!"


if __name__ == "__main__":

    if not args.deterministic:
        cudnn.benchmark = True
        cudnn.deterministic = False
    else:
        cudnn.benchmark = False
        cudnn.deterministic = True
    random.seed(args.seed)
    np.random.seed(args.seed)
    torch.manual_seed(args.seed)
    torch.cuda.manual_seed(args.seed)

    dataset_config = {
        'Synapse': {
            'Dataset': Synapse_dataset,
            'volume_path': '/home/ahu/桌面/file/TransFuseNet/TransFuseNet/data/Synapse/test_vol_h5',
            'list_dir': '/home/ahu/桌面/file/TransFuseNet/TransFuseNet/lists/lists_Synapse',
            'num_classes': 6,
            'z_spacing': 1,
        },
    }
    dataset_name = args.dataset
    args.num_classes = dataset_config[dataset_name]['num_classes']
    args.volume_path = dataset_config[dataset_name]['volume_path']
    args.Dataset = dataset_config[dataset_name]['Dataset']
    args.list_dir = dataset_config[dataset_name]['list_dir']
    args.z_spacing = dataset_config[dataset_name]['z_spacing']
    args.is_pretrain = True

    # name the same snapshot defined in train script!
    args.exp = 'TU_' + dataset_name + str(args.img_size)
    snapshot_path = "/home/ahu/桌面/file/TransUNet(1)/TransUNet/model/{}/{}".format(args.exp, 'TU')
    snapshot_path = snapshot_path + '_pretrain' if args.is_pretrain else snapshot_path
    snapshot_path += '_' + args.vit_name
    snapshot_path = snapshot_path + '_skip' + str(args.n_skip)
    snapshot_path = snapshot_path + '_vitpatch' + str(args.vit_patches_size) if args.vit_patches_size!=16 else snapshot_path
    snapshot_path = snapshot_path + '_epo' + str(args.max_epochs) if args.max_epochs != 30 else snapshot_path
    if dataset_name == 'ACDC':  # using max_epoch instead of iteration to control training duration
        snapshot_path = snapshot_path + '_' + str(args.max_iterations)[0:2] + 'k' if args.max_iterations != 30000 else snapshot_path
    snapshot_path = snapshot_path+'_bs'+str(args.batch_size)
    snapshot_path = snapshot_path + '_lr' + str(args.base_lr) if args.base_lr != 0.01 else snapshot_path
    snapshot_path = snapshot_path + '_'+str(args.img_size)
    snapshot_path = snapshot_path + '_s'+str(args.seed) if args.seed!=1234 else snapshot_path

    config_vit = CONFIGS_ViT_seg[args.vit_name]
    config_vit.n_classes = args.num_classes
    config_vit.n_skip = args.n_skip
    config_vit.patches.size = (args.vit_patches_size, args.vit_patches_size)
    if args.vit_name.find('R50') !=-1:
        config_vit.patches.grid = (int(args.img_size/args.vit_patches_size), int(args.img_size/args.vit_patches_size))
    net = ViT_seg(config_vit, img_size=args.img_size, num_classes=config_vit.n_classes).cuda()

    snapshot = os.path.join(snapshot_path, 'best_model.pth')
    if not os.path.exists(snapshot):
        snapshot = snapshot.replace('best_model', 'epoch_' + str(args.max_epochs - 1))
    snapshot = os.path.join(snapshot_path, 'best_model.pth')
    if not os.path.exists(snapshot):
        snapshot = snapshot.replace('best_model', 'epoch_' + str(args.max_epochs - 1))
    snapshot = '/home/ahu/桌面/file/TransFuseNet/TransFuseNet/model/TU_Synapse224/transmission/epoch_99.pth'
    net.load_state_dict(torch.load(snapshot))

    # 从snapshot路径中提取模型配置部分作为日志文件名
    # 1. 获取snapshot的目录名（包含模型配置）
    snapshot_dir = os.path.dirname(snapshot)
    # 2. 去掉目录名中的路径部分，只保留模型配置
    snapshot_name = os.path.basename(snapshot_dir)

    # 设置日志文件夹
    log_folder = './test_log/test_log_' + args.exp
    os.makedirs(log_folder, exist_ok=True)

    # 设置日志文件名，使用提取的模型配置名称
    logging.basicConfig(filename=os.path.join(log_folder, snapshot_name + ".txt"), level=logging.INFO,
                        format='[%(asctime)s.%(msecs)03d] %(message)s', datefmt='%H:%M:%S')
    logging.getLogger().addHandler(logging.StreamHandler(sys.stdout))
    logging.info(str(args))
    logging.info(snapshot_name)

    if args.is_savenii:
        args.test_save_dir = '/home/ahu/桌面/file/TransUNet(1)/TransUNet/predictions'
        test_save_path = os.path.join(args.test_save_dir, args.exp, snapshot_name)
        os.makedirs(test_save_path, exist_ok=True)
    else:
        test_save_path = None
    inference(args, net, test_save_path)


