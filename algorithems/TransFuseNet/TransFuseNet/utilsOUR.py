import numpy as np
import torch
from medpy import metric
from scipy.ndimage import zoom
import torch.nn as nn
import SimpleITK as sitk
from PIL import  Image
import time
from sklearn.metrics import recall_score, f1_score

class DiceLoss(nn.Module):
    def __init__(self, n_classes):
        super(DiceLoss, self).__init__()
        self.n_classes = n_classes

    def _one_hot_encoder(self, input_tensor):
        tensor_list = []
        for i in range(self.n_classes):
            temp_prob = input_tensor == i
            tensor_list.append(temp_prob.unsqueeze(1))
        output_tensor = torch.cat(tensor_list, dim=1)
        return output_tensor.float()

    def _dice_loss(self, score, target):
        target = target.float()
        smooth = 1e-5
        intersect = torch.sum(score * target)
        y_sum = torch.sum(target * target)
        z_sum = torch.sum(score * score)
        loss = (2 * intersect + smooth) / (z_sum + y_sum + smooth)
        loss = 1 - loss
        return loss

    def forward(self, inputs, target, weight=None, softmax=False):
        if softmax:
            inputs = torch.softmax(inputs, dim=1)
        target = self._one_hot_encoder(target)
        if weight is None:
            weight = [1] * self.n_classes
        assert inputs.size() == target.size(), 'predict {} & target {} shape do not match'.format(inputs.size(), target.size())
        class_wise_dice = []
        loss = 0.0
        for i in range(0, self.n_classes):
            dice = self._dice_loss(inputs[:, i], target[:, i])
            class_wise_dice.append(1.0 - dice.item())
            loss += dice * weight[i]
        return loss / self.n_classes

def calculate_metric_percase(pred, gt):
    pred[pred > 0] = 1
    gt[gt > 0] = 1

    tp = np.sum((pred == 1) & (gt == 1))
    tn = np.sum((pred == 0) & (gt == 0))
    fp = np.sum((pred == 1) & (gt == 0))
    fn = np.sum((pred == 0) & (gt == 1))

    if pred.sum() > 0 and gt.sum() > 0:
        dice = metric.binary.dc(pred, gt)
        hd95 = metric.binary.hd95(pred, gt)
        acc = metric.binary.acc(pred, gt)
        iou = metric.binary.iou(pred, gt)
        oa = (tp + tn) / (tp + tn + fp + fn + 1e-6)
        return dice, hd95, acc, iou, oa
    elif pred.sum() > 0 and gt.sum() == 0:
        return 1, 0, 1, 1, 1
    else:
        return 0, 0, 0, 0, 0

def calculate_additional_metrics(pred, gt, num_classes):
    """
    Calculate PA, Recall, and F1-score for each class.
    """
    metrics = []
    for i in range(num_classes):
        # Flatten the prediction and ground truth for the current class
        pred_flat = (pred == i).flatten()
        gt_flat = (gt == i).flatten()

        # Calculate True Positives, False Positives, and False Negatives
        tp = np.sum((pred_flat == 1) & (gt_flat == 1))
        fp = np.sum((pred_flat == 1) & (gt_flat == 0))
        fn = np.sum((pred_flat == 0) & (gt_flat == 1))

        # Calculate PA, Recall, and F1-score for the current class
        pa = np.mean(pred_flat == gt_flat) if np.sum(gt_flat) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0

        metrics.append((pa, recall, f1))

    return metrics
def test_single_volume(image, label, net, classes, patch_size=[256, 256], test_save_path=None, case=None, z_spacing=1):
    image, label = image.squeeze(0).cpu().detach().numpy(), label.squeeze(0).cpu().detach().numpy()
    _, x, y = image.shape
    if x != patch_size[0] or y != patch_size[1]:
        image = zoom(image, (1, patch_size[0] / x, patch_size[1] / y), order=3)
    input = torch.from_numpy(image).unsqueeze(0).float().cuda()
    net.eval()

    start_time = time.time()
    with torch.no_grad():
        out = torch.argmax(torch.softmax(net(input), dim=1), dim=1).squeeze(0)
        out = out.cpu().detach().numpy()
        if x != patch_size[0] or y != patch_size[1]:
            prediction = zoom(out, (x / patch_size[0], y / patch_size[1]), order=0)
        else:
            prediction = out
    end_time = time.time()
    infer_time = end_time - start_time

    metric_list = []
    for i in range(classes):
        metric_list.append(calculate_metric_percase(prediction == i, label == i))

    # Calculate PA, Recall, and F1-score for each class
    class_metrics = calculate_additional_metrics(prediction, label, classes)
    pa_list = [metric[0] for metric in class_metrics]
    recall_list = [metric[1] for metric in class_metrics]
    f1_list = [metric[2] for metric in class_metrics]

    if test_save_path is not None:
        colors = {
            0: [255, 255, 255],
            1: [255, 0, 0],
            2: [255, 255, 0],
            3: [0, 255, 0],
            4: [0, 255, 255],
            5 :[0, 0, 225], # background (black)
        }
        img_r = np.zeros_like(prediction)
        img_g = np.zeros_like(prediction)
        img_b = np.zeros_like(prediction)

        for cls, color in colors.items():
            img_r[prediction == cls] = color[0]
            img_g[prediction == cls] = color[1]
            img_b[prediction == cls] = color[2]

        img_r = Image.fromarray(np.uint8(img_r)).convert('L')
        img_g = Image.fromarray(np.uint8(img_g)).convert('L')
        img_b = Image.fromarray(np.uint8(img_b)).convert('L')
        prediction = Image.merge('RGB', [img_r, img_g, img_b])

        prediction.save(test_save_path + '/' + case + '.png')

    return metric_list, infer_time, pa_list, recall_list, f1_list